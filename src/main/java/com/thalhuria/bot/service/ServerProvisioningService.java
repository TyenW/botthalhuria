package com.thalhuria.bot.service;

import com.thalhuria.bot.config.*;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Motor central de provisionamento, sincronização idempotente e limpeza de infraestrutura do servidor Discord.
 *
 * Cada item do template é localizado primeiro pelo ID salvo no banco (via "key") e só depois pelo nome.
 * Assim, renomear um cargo/canal no template renomeia o item existente em vez de criar um duplicado.
 */
public class ServerProvisioningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProvisioningService.class);
    private final ServerMapRepository repository;
    private final AuditLogService auditLogService;
    private final TemplateService templateService;
    private final PanelService panelService;
    private ModerationService moderationService;
    private StatsCounterService statsCounterService;

    public record ProvisioningReport(
            int rolesCreated,
            int rolesUpdated,
            int categoriesCreated,
            int categoriesUpdated,
            int channelsCreated,
            int channelsUpdated,
            int panelsPublished,
            int automodRules,
            long executionTimeMs,
            List<String> logs
    ) {
        public long errorCount() {
            return logs.stream().filter(l -> l.startsWith("ERRO")).count();
        }
    }

    public ServerProvisioningService(ServerMapRepository repository, AuditLogService auditLogService,
                                     TemplateService templateService, PanelService panelService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.templateService = templateService;
        this.panelService = panelService;
    }

    /** Ligados depois da construção para evitar dependência circular na inicialização. */
    public void setCommunityServices(ModerationService moderationService, StatsCounterService statsCounterService) {
        this.moderationService = moderationService;
        this.statsCounterService = statsCounterService;
    }

    /**
     * Provisiona ou sincroniza a estrutura do servidor com base no template fornecido.
     * Operação idempotente: nunca duplica cargos ou canais existentes; apenas atualiza o que for necessário.
     */
    public CompletableFuture<ProvisioningReport> provisionServer(Guild guild, ServerTemplate template, boolean isSyncOnly) {
        return CompletableFuture.supplyAsync(() -> {
            auditLogService.suppress(guild);
            try {
                return doProvision(guild, template, isSyncOnly);
            } finally {
                auditLogService.release(guild);
            }
        });
    }

    private ProvisioningReport doProvision(Guild guild, ServerTemplate template, boolean isSyncOnly) {
        long startTime = System.currentTimeMillis();
        List<String> logMessages = new ArrayList<>();

        int rolesCreated = 0;
        int rolesUpdated = 0;
        int categoriesCreated = 0;
        int categoriesUpdated = 0;
        int channelsCreated = 0;
        int channelsUpdated = 0;

        logMessages.add("Iniciando " + (isSyncOnly ? "sincronização" : "provisionamento") + " para a guilda: " + guild.getName());

        // 1. PROCESSAR CARGOS (Roles)
        Map<String, Role> resolvedRoles = new HashMap<>();
        resolvedRoles.put("@everyone", guild.getPublicRole());
        List<Role> orderedRoles = new ArrayList<>();

        for (RoleTemplate roleTpl : template.getRoles()) {
            try {
                Color roleColor = TemplateService.parseColor(roleTpl.getColorHex(), null);
                Set<Permission> permissions = PermissionParser.parseAll(roleTpl.getPermissions());

                Role role = findMapped(guild, "ROLE", roleTpl.getKey(), guild::getRoleById)
                        .orElseGet(() -> guild.getRolesByName(roleTpl.getName(), true).stream().findFirst().orElse(null));

                if (role != null) {
                    if (updateRole(guild, role, roleTpl, roleColor, permissions)) {
                        logMessages.add("Cargo atualizado: " + roleTpl.getName());
                    }
                    rolesUpdated++;
                } else {
                    role = guild.createRole()
                            .setName(roleTpl.getName())
                            .setColor(roleColor)
                            .setHoisted(roleTpl.isHoist())
                            .setMentionable(roleTpl.isMentionable())
                            .setPermissions(permissions)
                            .complete();
                    rolesCreated++;
                    logMessages.add("Cargo criado: " + role.getName());
                }

                resolvedRoles.put(roleTpl.getName(), role);
                resolvedRoles.put(roleTpl.getKey(), role);
                orderedRoles.add(role);

                repository.saveMapping(guild.getId(), "ROLE", roleTpl.getKey(), role.getId(), roleTpl.getName());
                if (roleTpl.isMemberRole()) {
                    repository.saveMapping(guild.getId(), "METADATA", "MEMBER_ROLE", role.getId(), role.getName());
                }
                if (roleTpl.isAdminRole()) {
                    repository.saveMapping(guild.getId(), "METADATA", "ADMIN_ROLE", role.getId(), role.getName());
                }
            } catch (Exception e) {
                LOGGER.error("Erro ao provisionar cargo {}: {}", roleTpl.getName(), e.getMessage(), e);
                logMessages.add("ERRO no cargo " + roleTpl.getName() + ": " + e.getMessage());
            }
        }

        try {
            orderRoles(guild, orderedRoles);
        } catch (Exception e) {
            LOGGER.warn("Não foi possível ordenar a hierarquia de cargos: {}", e.getMessage());
            logMessages.add("ERRO ao ordenar cargos: " + e.getMessage());
        }

        // 2. PROCESSAR CATEGORIAS E CANAIS
        Map<String, TextChannel> communityChannels = communityChannels(guild, template);
        for (int catIndex = 0; catIndex < template.getCategories().size(); catIndex++) {
            CategoryTemplate catTpl = template.getCategories().get(catIndex);
            try {
                Category category = findMapped(guild, "CATEGORY", catTpl.getKey(), guild::getCategoryById)
                        .orElseGet(() -> guild.getCategoriesByName(catTpl.getName(), true).stream().findFirst().orElse(null));

                if (category != null) {
                    if (!category.getName().equals(catTpl.getName())) {
                        category.getManager().setName(catTpl.getName()).queue();
                        logMessages.add("Categoria renomeada: " + category.getName() + " -> " + catTpl.getName());
                    }
                    categoriesUpdated++;
                } else {
                    category = guild.createCategory(catTpl.getName()).complete();
                    categoriesCreated++;
                    logMessages.add("Categoria criada: " + category.getName());
                }

                int desiredPosition = catTpl.getPosition() > 0 ? catTpl.getPosition() : catIndex + 1;
                if (category.getPositionRaw() != desiredPosition) {
                    category.getManager().setPosition(desiredPosition).queue();
                }

                repository.saveMapping(guild.getId(), "CATEGORY", catTpl.getKey(), category.getId(), catTpl.getName());
                if (catTpl.isIndividualRoomsCategory()) {
                    repository.saveMapping(guild.getId(), "METADATA", "INDIVIDUAL_ROOMS_CATEGORY", category.getId(), catTpl.getName());
                }

                upsertOverrides(guild, category, catTpl.getPermissionOverrides(), resolvedRoles);

                // 3. PROCESSAR CANAIS DENTRO DA CATEGORIA
                List<StandardGuildChannel> resolvedChannels = new ArrayList<>();
                for (ChannelTemplate chanTpl : catTpl.getChannels()) {
                    try {
                        final Category parent = category;
                        StandardGuildChannel channel = findMapped(guild, "CHANNEL", chanTpl.getKey(),
                                id -> guild.getChannelById(StandardGuildChannel.class, id))
                                .orElseGet(() -> findChannelInCategory(parent, chanTpl.getName()));

                        // Canais oficiais da Comunidade (regras/atualizações) são adotados em vez de duplicados
                        TextChannel official = communityChannels.get(chanTpl.getKey().toUpperCase());
                        if (official != null && !official.equals(channel)) {
                            if (channel != null) {
                                channel.delete().reason("Substituído pelo canal oficial da Comunidade").queue(null, err -> { });
                                logMessages.add("Canal duplicado removido: #" + channel.getName());
                            }
                            channel = official;
                            logMessages.add("Canal da Comunidade adotado: #" + official.getName() + " → " + chanTpl.getName());
                        }

                        String topic = templateService.render(guild, chanTpl.getTopic());
                        if (channel == null) {
                            channel = createChannel(guild, category, chanTpl, topic);
                            channelsCreated++;
                            logMessages.add("Canal criado: #" + chanTpl.getName() + " (" + chanTpl.getType() + ")");
                        } else {
                            if (updateChannel(channel, category, chanTpl, topic)) {
                                logMessages.add("Canal atualizado: #" + chanTpl.getName());
                            }
                            channelsUpdated++;
                        }
                        resolvedChannels.add(channel);

                        applyChannelPermissions(guild, channel, category, chanTpl.getPermissionOverrides(), resolvedRoles);

                        repository.saveMapping(guild.getId(), "CHANNEL", chanTpl.getKey(), channel.getId(), chanTpl.getName());
                        if (chanTpl.isSystemLogChannel()) {
                            repository.saveMapping(guild.getId(), "METADATA", "LOG_CHANNEL", channel.getId(), channel.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Erro ao provisionar canal {}: {}", chanTpl.getName(), e.getMessage(), e);
                        logMessages.add("ERRO no canal " + chanTpl.getName() + ": " + e.getMessage());
                    }
                }
                orderChannels(resolvedChannels);
            } catch (Exception e) {
                LOGGER.error("Erro ao provisionar categoria {}: {}", catTpl.getName(), e.getMessage(), e);
                logMessages.add("ERRO na categoria " + catTpl.getName() + ": " + e.getMessage());
            }
        }

        // 4. Bots que já estão no servidor recebem o cargo de bot; o dono recebe o cargo de dono
        assignBotRole(guild, template, resolvedRoles);
        assignOwnerRole(guild, template, resolvedRoles);

        // 5. Painéis (regras, verificação, cargos, tickets...)
        templateService.invalidate(guild);
        int panelsPublished = 0;
        try {
            panelsPublished = panelService.publishAll(guild, template);
        } catch (Exception e) {
            LOGGER.error("Erro ao publicar painéis: {}", e.getMessage(), e);
            logMessages.add("ERRO nos painéis: " + e.getMessage());
        }

        // 6. AutoMod nativo e contadores
        int automodRules = 0;
        if (moderationService != null) {
            try {
                automodRules = moderationService.syncAutoMod(guild, template);
            } catch (Exception e) {
                LOGGER.warn("Falha ao sincronizar AutoMod: {}", e.getMessage());
                logMessages.add("ERRO no AutoMod: " + e.getMessage());
            }
        }
        if (statsCounterService != null) {
            try {
                statsCounterService.update(guild);
            } catch (Exception e) {
                LOGGER.debug("Contadores não atualizados agora: {}", e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logMessages.add(String.format("Finalizado em %d ms! Cargos: %d criados / %d atualizados. Categorias: %d criadas. Canais: %d criados / %d atualizados. Painéis: %d.",
                duration, rolesCreated, rolesUpdated, categoriesCreated, channelsCreated, channelsUpdated, panelsPublished));

        return new ProvisioningReport(
                rolesCreated, rolesUpdated,
                categoriesCreated, categoriesUpdated,
                channelsCreated, channelsUpdated,
                panelsPublished, automodRules, duration, logMessages
        );
    }

    /**
     * Em servidores com Comunidade, o Discord exige um canal de regras e um de atualizações que não podem ser apagados.
     * Esses canais assumem o papel das keys onboarding.rulesChannelKey / communityUpdatesChannelKey do template
     * (são renomeados, movidos e recebem as permissões e o painel do template).
     */
    private Map<String, TextChannel> communityChannels(Guild guild, ServerTemplate template) {
        Map<String, TextChannel> map = new HashMap<>();
        if (!guild.getFeatures().contains("COMMUNITY")) return map;
        OnboardingConfig config = template.getOnboarding();
        adopt(map, template, config.getRulesChannelKey(), guild.getRulesChannel());
        adopt(map, template, config.getCommunityUpdatesChannelKey(), guild.getCommunityUpdatesChannel());
        return map;
    }

    private static void adopt(Map<String, TextChannel> map, ServerTemplate template, String key, TextChannel official) {
        if (key == null || key.isBlank() || official == null) return;
        // Só canais de texto podem cumprir esse papel
        boolean textInTemplate = template.findChannel(key)
                .map(c -> c.getType() == null || "TEXT".equalsIgnoreCase(c.getType()))
                .orElse(false);
        if (textInTemplate) map.put(key.toUpperCase(), official);
    }

    // ---------------------------------------------------------------- simulação

    /**
     * Mostra o que o /sync-server faria, sem alterar nada no servidor.
     */
    public List<String> previewSync(Guild guild, ServerTemplate template) {
        List<String> plan = new ArrayList<>();
        for (RoleTemplate tpl : template.getRoles()) {
            Role role = findMapped(guild, "ROLE", tpl.getKey(), guild::getRoleById)
                    .orElseGet(() -> guild.getRolesByName(tpl.getName(), true).stream().findFirst().orElse(null));
            if (role == null) {
                plan.add("➕ Criar cargo **" + tpl.getName() + "**");
                continue;
            }
            if (!role.getName().equals(tpl.getName())) plan.add("✏️ Renomear cargo " + role.getName() + " → **" + tpl.getName() + "**");
            Color color = TemplateService.parseColor(tpl.getColorHex(), null);
            int desiredColor = color == null ? Role.DEFAULT_COLOR_RAW : color.getRGB() & 0xFFFFFF;
            if (role.getColorRaw() != desiredColor) plan.add("🎨 Mudar cor de **" + tpl.getName() + "**");
            if (role.getPermissionsRaw() != Permission.getRaw(PermissionParser.parseAll(tpl.getPermissions()))) {
                plan.add("🔑 Ajustar permissões de **" + tpl.getName() + "**");
            }
            if (role.isHoisted() != tpl.isHoist() || role.isMentionable() != tpl.isMentionable()) {
                plan.add("⚙️ Ajustar exibição/menção de **" + tpl.getName() + "**");
            }
        }
        for (CategoryTemplate catTpl : template.getCategories()) {
            Category category = findMapped(guild, "CATEGORY", catTpl.getKey(), guild::getCategoryById)
                    .orElseGet(() -> guild.getCategoriesByName(catTpl.getName(), true).stream().findFirst().orElse(null));
            if (category == null) {
                plan.add("➕ Criar categoria **" + catTpl.getName() + "** com " + catTpl.getChannels().size() + " canal(is)");
                continue;
            }
            if (!category.getName().equals(catTpl.getName())) plan.add("✏️ Renomear categoria " + category.getName() + " → **" + catTpl.getName() + "**");
            for (ChannelTemplate chanTpl : catTpl.getChannels()) {
                StandardGuildChannel channel = findMapped(guild, "CHANNEL", chanTpl.getKey(),
                        id -> guild.getChannelById(StandardGuildChannel.class, id))
                        .orElseGet(() -> findChannelInCategory(category, chanTpl.getName()));
                TextChannel official = communityChannels(guild, template).get(chanTpl.getKey().toUpperCase());
                if (official != null && !official.equals(channel)) {
                    plan.add("🏛️ Adotar o canal oficial da Comunidade #" + official.getName() + " como **" + chanTpl.getName() + "**"
                            + (channel != null ? " (e remover a cópia #" + channel.getName() + ")" : ""));
                    continue;
                }
                if (channel == null) {
                    plan.add("➕ Criar canal **" + chanTpl.getName() + "** em " + catTpl.getName());
                    continue;
                }
                if (chanTpl.getCounter() == null && !(channel instanceof VoiceChannel ? channel.getName().equals(chanTpl.getName()) : sameName(channel, chanTpl.getName()))) {
                    plan.add("✏️ Renomear canal " + channel.getName() + " → **" + chanTpl.getName() + "**");
                }
                if (channel.getParentCategoryIdLong() != category.getIdLong()) {
                    plan.add("📦 Mover **" + chanTpl.getName() + "** para " + catTpl.getName());
                }
            }
        }
        long missingPanels = template.allChannels()
                .filter(c -> c.getPanel() != null && repository.getDiscordId(guild.getId(), "PANEL", c.getKey()).isEmpty())
                .count();
        if (missingPanels > 0) plan.add("🧩 Publicar " + missingPanels + " painel(is) novo(s)");
        plan.add("🔄 Atualizar o texto de todos os painéis, permissões e regras de AutoMod");
        return plan;
    }

    // ---------------------------------------------------------------- cargos

    /**
     * Envia ao Discord apenas os campos que mudaram. Retorna true se algo foi alterado.
     */
    private boolean updateRole(Guild guild, Role role, RoleTemplate tpl, Color color, Set<Permission> permissions) {
        if (role.isManaged() || !guild.getSelfMember().canInteract(role)) {
            LOGGER.warn("Cargo {} está acima do cargo do bot ou é gerenciado por integração; não será alterado.", role.getName());
            return false;
        }
        int desiredColor = color == null ? Role.DEFAULT_COLOR_RAW : color.getRGB() & 0xFFFFFF;
        long desiredPerms = Permission.getRaw(permissions);

        var manager = role.getManager();
        boolean changed = false;
        if (!role.getName().equals(tpl.getName())) { manager.setName(tpl.getName()); changed = true; }
        if (role.getColorRaw() != desiredColor) { manager.setColor(color); changed = true; }
        if (role.isHoisted() != tpl.isHoist()) { manager.setHoisted(tpl.isHoist()); changed = true; }
        if (role.isMentionable() != tpl.isMentionable()) { manager.setMentionable(tpl.isMentionable()); changed = true; }
        if (role.getPermissionsRaw() != desiredPerms) { manager.setPermissions(permissions); changed = true; }
        if (changed) manager.queue(null, err -> LOGGER.warn("Falha ao atualizar cargo {}: {}", tpl.getName(), err.getMessage()));
        return changed;
    }

    /**
     * Coloca os cargos na ordem do template (primeiro = mais alto), logo abaixo do cargo mais alto do bot.
     */
    private void orderRoles(Guild guild, List<Role> templateOrder) {
        Member self = guild.getSelfMember();
        if (self.getRoles().isEmpty()) return;
        Role botTop = self.getRoles().get(0);

        List<Role> manageable = templateOrder.stream()
                .filter(r -> !r.isManaged() && self.canInteract(r))
                .toList();

        boolean alreadyOrdered = true;
        for (int i = 0; i + 1 < manageable.size(); i++) {
            if (manageable.get(i).getPosition() <= manageable.get(i + 1).getPosition()) {
                alreadyOrdered = false;
                break;
            }
        }
        if (alreadyOrdered) return;

        RoleOrderAction action = guild.modifyRolePositions(false); // índice 0 = cargo mais alto
        int base = action.getCurrentOrder().indexOf(botTop);
        for (int i = 0; i < manageable.size(); i++) {
            action.selectPosition(manageable.get(i)).moveTo(base + 1 + i);
        }
        action.complete();
        LOGGER.info("Hierarquia de cargos reorganizada conforme o template");
    }

    private void assignOwnerRole(Guild guild, ServerTemplate template, Map<String, Role> resolvedRoles) {
        Optional<RoleTemplate> ownerTpl = template.getRoles().stream().filter(RoleTemplate::isOwnerRole).findFirst();
        if (ownerTpl.isEmpty()) return;
        Role ownerRole = resolvedRoles.get(ownerTpl.get().getKey());
        if (ownerRole == null || !guild.getSelfMember().canInteract(ownerRole)) return;
        try {
            Member owner = guild.retrieveOwner().complete();
            if (!owner.getRoles().contains(ownerRole)) {
                guild.addRoleToMember(owner, ownerRole).reason("Cargo do dono do servidor").complete();
            }
        } catch (Exception e) {
            LOGGER.warn("Não foi possível dar o cargo de dono: {}", e.getMessage());
        }
    }

    private void assignBotRole(Guild guild, ServerTemplate template, Map<String, Role> resolvedRoles) {
        String key = template.getOnboarding().getBotRoleKey();
        Role botRole = key == null ? null : resolvedRoles.get(key);
        if (botRole == null || !guild.getSelfMember().canInteract(botRole)) return;
        for (Member member : guild.getMembers()) {
            if (member.getUser().isBot() && !member.getRoles().contains(botRole)) {
                guild.addRoleToMember(member, botRole).reason("Cargo de bot do template").queue(null, err -> { });
            }
        }
    }

    // ---------------------------------------------------------------- canais

    private StandardGuildChannel createChannel(Guild guild, Category category, ChannelTemplate chanTpl, String topic) {
        String type = chanTpl.getType() != null ? chanTpl.getType().toUpperCase() : "TEXT";
        boolean newsAvailable = guild.getFeatures().contains("COMMUNITY") || guild.getFeatures().contains("NEWS");
        if (("ANNOUNCEMENT".equals(type) || "NEWS".equals(type)) && !newsAvailable) {
            LOGGER.warn("Servidor sem Comunidade ativada: #{} será criado como canal de texto comum", chanTpl.getName());
            type = "TEXT";
        }
        return switch (type) {
            case "VOICE" -> category.createVoiceChannel(chanTpl.getCounter() != null
                            ? chanTpl.getName().replace("{value}", "…") : chanTpl.getName())
                    .setUserlimit(chanTpl.getUserLimit())
                    .setNSFW(chanTpl.isNsfw())
                    .complete();
            case "ANNOUNCEMENT", "NEWS" -> category.createNewsChannel(chanTpl.getName())
                    .setTopic(emptyToNull(topic))
                    .setNSFW(chanTpl.isNsfw())
                    .complete();
            default -> category.createTextChannel(chanTpl.getName())
                    .setTopic(emptyToNull(topic))
                    .setSlowmode(chanTpl.getSlowmode())
                    .setNSFW(chanTpl.isNsfw())
                    .complete();
        };
    }

    /**
     * Ajusta nome, categoria, tópico, slowmode, NSFW e limite de usuários. Retorna true se algo mudou.
     * Renomear canais tem rate limit agressivo no Discord (2 a cada 10 min), por isso só envia quando realmente difere.
     */
    private boolean updateChannel(StandardGuildChannel channel, Category category, ChannelTemplate tpl, String topic) {
        boolean changed = false;
        if (channel instanceof TextChannel tc) {
            var m = tc.getManager();
            if (!sameName(tc, tpl.getName())) { m.setName(tpl.getName()); changed = true; }
            if (tc.getParentCategoryIdLong() != category.getIdLong()) { m.setParent(category); changed = true; }
            if (!Objects.equals(nullToEmpty(tc.getTopic()), nullToEmpty(topic))) { m.setTopic(emptyToNull(topic)); changed = true; }
            if (tc.getSlowmode() != tpl.getSlowmode()) { m.setSlowmode(tpl.getSlowmode()); changed = true; }
            if (tc.isNSFW() != tpl.isNsfw()) { m.setNSFW(tpl.isNsfw()); changed = true; }
            if (changed) m.queue(null, err -> LOGGER.warn("Falha ao atualizar #{}: {}", tpl.getName(), err.getMessage()));
        } else if (channel instanceof NewsChannel nc) {
            var m = nc.getManager();
            if (!sameName(nc, tpl.getName())) { m.setName(tpl.getName()); changed = true; }
            if (nc.getParentCategoryIdLong() != category.getIdLong()) { m.setParent(category); changed = true; }
            if (!Objects.equals(nullToEmpty(nc.getTopic()), nullToEmpty(topic))) { m.setTopic(emptyToNull(topic)); changed = true; }
            if (nc.isNSFW() != tpl.isNsfw()) { m.setNSFW(tpl.isNsfw()); changed = true; }
            if (changed) m.queue(null, err -> LOGGER.warn("Falha ao atualizar #{}: {}", tpl.getName(), err.getMessage()));
        } else if (channel instanceof VoiceChannel vc) {
            var m = vc.getManager();
            if (tpl.getCounter() == null && !vc.getName().equals(tpl.getName())) { m.setName(tpl.getName()); changed = true; }
            if (vc.getParentCategoryIdLong() != category.getIdLong()) { m.setParent(category); changed = true; }
            if (vc.getUserLimit() != tpl.getUserLimit()) { m.setUserLimit(tpl.getUserLimit()); changed = true; }
            if (vc.isNSFW() != tpl.isNsfw()) { m.setNSFW(tpl.isNsfw()); changed = true; }
            if (changed) m.queue(null, err -> LOGGER.warn("Falha ao atualizar {}: {}", tpl.getName(), err.getMessage()));
        }
        return changed;
    }

    /**
     * Garante que os canais apareçam na ordem do template. Só reposiciona se a ordem atual estiver diferente.
     */
    private void orderChannels(List<StandardGuildChannel> templateOrder) {
        // Texto e voz são ordenados separadamente pelo Discord
        for (boolean voice : new boolean[]{false, true}) {
            List<StandardGuildChannel> bucket = templateOrder.stream()
                    .filter(ch -> (ch instanceof VoiceChannel) == voice)
                    .toList();
            boolean ordered = true;
            for (int i = 0; i + 1 < bucket.size(); i++) {
                if (bucket.get(i).getPosition() >= bucket.get(i + 1).getPosition()) {
                    ordered = false;
                    break;
                }
            }
            if (ordered) continue;
            for (int i = 0; i < bucket.size(); i++) {
                bucket.get(i).getManager().setPosition(i).queue(null, err -> { });
            }
        }
    }

    private static boolean sameName(GuildChannel channel, String templateName) {
        // O Discord normaliza nomes de canais de texto: minúsculas e espaços viram hífens
        String normalized = templateName.trim().toLowerCase().replaceAll("\\s+", "-");
        return channel.getName().equalsIgnoreCase(normalized);
    }

    private StandardGuildChannel findChannelInCategory(Category category, String name) {
        String normalized = name.trim().toLowerCase().replaceAll("\\s+", "-");
        for (GuildChannel ch : category.getChannels()) {
            if ((ch.getName().equalsIgnoreCase(name) || ch.getName().equalsIgnoreCase(normalized))
                    && ch instanceof StandardGuildChannel sgc) {
                return sgc;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- permissões

    /**
     * Aplica uma lista de PermissionOverrides a uma categoria ou canal.
     */
    private void upsertOverrides(Guild guild, IPermissionContainer target, List<PermissionRule> rules, Map<String, Role> resolvedRoles) {
        if (rules == null || rules.isEmpty()) return;

        for (PermissionRule rule : rules) {
            IPermissionHolder holder = resolvePermissionHolder(guild, rule.getTarget(), resolvedRoles);
            if (holder == null) {
                LOGGER.warn("Não foi possível resolver alvo de permissão: {}", rule.getTarget());
                continue;
            }

            Set<Permission> allowed = PermissionParser.parseAll(rule.getAllow());
            Set<Permission> denied = PermissionParser.parseAll(rule.getDeny());

            PermissionOverride current = target.getPermissionOverride(holder);
            if (current != null && current.getAllowedRaw() == Permission.getRaw(allowed) && current.getDeniedRaw() == Permission.getRaw(denied)) {
                continue;
            }
            try {
                target.upsertPermissionOverride(holder).setPermissions(allowed, denied).complete();
            } catch (Exception e) {
                LOGGER.error("Falha ao aplicar permissões para {} em {}: {}", rule.getTarget(), target.getName(), e.getMessage());
            }
        }
    }

    private record OverrideSpec(long holderId, boolean role, long allow, long deny) {}

    /**
     * Permissões finais do canal = overrides da categoria + regras do próprio canal por cima (mesmo alvo substitui).
     * Sem regras próprias, equivale ao botão "Sincronizar com a categoria" do Discord.
     * Envia uma única requisição e somente se o estado atual for diferente do desejado.
     * O template é a fonte da verdade: overrides adicionados manualmente em canais do template são removidos.
     */
    private void applyChannelPermissions(Guild guild, StandardGuildChannel channel, Category category,
                                         List<PermissionRule> rules, Map<String, Role> resolvedRoles) {
        Map<Long, OverrideSpec> desired = new LinkedHashMap<>();
        for (PermissionOverride o : category.getPermissionOverrides()) {
            desired.put(o.getIdLong(), new OverrideSpec(o.getIdLong(), o.isRoleOverride(), o.getAllowedRaw(), o.getDeniedRaw()));
        }
        for (PermissionRule rule : rules) {
            IPermissionHolder holder = resolvePermissionHolder(guild, rule.getTarget(), resolvedRoles);
            if (holder == null) {
                LOGGER.warn("Não foi possível resolver alvo de permissão: {}", rule.getTarget());
                continue;
            }
            desired.put(holder.getIdLong(), new OverrideSpec(holder.getIdLong(), holder instanceof Role,
                    Permission.getRaw(PermissionParser.parseAll(rule.getAllow())),
                    Permission.getRaw(PermissionParser.parseAll(rule.getDeny()))));
        }

        Map<Long, OverrideSpec> current = new HashMap<>();
        for (PermissionOverride o : channel.getPermissionOverrides()) {
            current.put(o.getIdLong(), new OverrideSpec(o.getIdLong(), o.isRoleOverride(), o.getAllowedRaw(), o.getDeniedRaw()));
        }
        if (current.equals(desired)) return;

        var manager = channel.getManager();
        for (OverrideSpec o : desired.values()) {
            if (o.role()) {
                manager.putRolePermissionOverride(o.holderId(), o.allow(), o.deny());
            } else {
                manager.putMemberPermissionOverride(o.holderId(), o.allow(), o.deny());
            }
        }
        for (Long holderId : current.keySet()) {
            if (!desired.containsKey(holderId)) manager.removePermissionOverride(holderId);
        }
        manager.queue(null, err -> LOGGER.warn("Falha ao aplicar permissões em #{}: {}", channel.getName(), err.getMessage()));
    }

    private IPermissionHolder resolvePermissionHolder(Guild guild, String targetName, Map<String, Role> resolvedRoles) {
        if ("@everyone".equalsIgnoreCase(targetName) || "everyone".equalsIgnoreCase(targetName)) {
            return guild.getPublicRole();
        }
        if (resolvedRoles.containsKey(targetName)) {
            return resolvedRoles.get(targetName);
        }
        List<Role> byName = guild.getRolesByName(targetName, true);
        if (!byName.isEmpty()) {
            return byName.get(0);
        }
        return null;
    }

    // ---------------------------------------------------------------- reset

    /**
     * Reseta e apaga com segurança canais, categorias e cargos gerenciados pelo bot no servidor.
     * Canais criados dentro das categorias do bot (salas privadas, tickets, salas temporárias) também são removidos.
     */
    public CompletableFuture<Void> resetServer(Guild guild) {
        return CompletableFuture.runAsync(() -> {
            auditLogService.suppress(guild);
            try {
                doReset(guild);
            } finally {
                auditLogService.release(guild);
                templateService.invalidate(guild);
            }
        });
    }

    /**
     * Resultado de uma limpeza: o que foi apagado e o que precisou ser mantido (com o motivo).
     */
    public record WipeResult(int channelsDeleted, int rolesDeleted, List<String> kept) {}

    public record RebuildReport(WipeResult wipe, ProvisioningReport provisioning) {}

    /**
     * Apaga e reconstrói o servidor do zero a partir do template, numa única operação.
     *
     * @param fullWipe true = apaga TODOS os canais e cargos do servidor (não só os criados pelo bot)
     */
    public CompletableFuture<RebuildReport> rebuildServer(Guild guild, ServerTemplate template, boolean fullWipe) {
        return CompletableFuture.supplyAsync(() -> {
            auditLogService.suppress(guild);
            try {
                WipeResult wipe = fullWipe ? doWipeAll(guild) : doReset(guild);
                templateService.invalidate(guild);
                LOGGER.info("Limpeza concluída ({} canais, {} cargos). Reconstruindo {}...",
                        wipe.channelsDeleted(), wipe.rolesDeleted(), guild.getName());
                ProvisioningReport report = doProvision(guild, template, false);
                if (fullWipe) wipe = handOverCommunityChannels(guild, template, wipe);
                return new RebuildReport(wipe, report);
            } finally {
                auditLogService.release(guild);
            }
        });
    }

    /**
     * Em servidores com Comunidade, os canais de regras e de atualizações não podem ser apagados.
     * Depois de criar o template, aponta essas funções para os canais novos e apaga os antigos.
     */
    private WipeResult handOverCommunityChannels(Guild guild, ServerTemplate template, WipeResult wipe) {
        if (!guild.getFeatures().contains("COMMUNITY")) return wipe;
        OnboardingConfig config = template.getOnboarding();
        TextChannel newRules = mappedTextChannel(guild, config.getRulesChannelKey());
        TextChannel newUpdates = mappedTextChannel(guild, config.getCommunityUpdatesChannelKey());
        if (newRules == null || newUpdates == null) return wipe;

        TextChannel oldRules = guild.getRulesChannel();
        TextChannel oldUpdates = guild.getCommunityUpdatesChannel();
        try {
            guild.getManager().setRulesChannel(newRules).setCommunityUpdatesChannel(newUpdates).complete();
        } catch (Exception e) {
            LOGGER.warn("Não foi possível trocar os canais da Comunidade: {}", e.getMessage());
            return wipe;
        }

        int deleted = 0;
        List<String> kept = new ArrayList<>(wipe.kept());
        for (TextChannel old : List.of(oldRules, oldUpdates).stream().filter(Objects::nonNull).distinct().toList()) {
            if (old.equals(newRules) || old.equals(newUpdates)) continue;
            if (deleteChannelQuietly(old, old.getName(), kept)) {
                deleted++;
                kept.removeIf(entry -> entry.startsWith("#" + old.getName() + " ("));
            }
        }
        LOGGER.info("Canais da Comunidade agora são #{} (regras) e #{} (atualizações)", newRules.getName(), newUpdates.getName());
        return new WipeResult(wipe.channelsDeleted() + deleted, wipe.rolesDeleted(), kept);
    }

    private TextChannel mappedTextChannel(Guild guild, String key) {
        if (key == null || key.isBlank()) return null;
        return repository.getDiscordId(guild.getId(), "CHANNEL", key).map(guild::getTextChannelById).orElse(null);
    }

    private WipeResult doReset(Guild guild) {
        LOGGER.info("Iniciando limpeza total (reset) do servidor {}", guild.getName());
        int channels = 0;
        int roles = 0;
        List<String> kept = new ArrayList<>();

        var channelMappings = repository.getMappingsByType(guild.getId(), "CHANNEL");
        var catMappings = repository.getMappingsByType(guild.getId(), "CATEGORY");
        var roleMappings = repository.getMappingsByType(guild.getId(), "ROLE");

        // 1. Deletar canais mapeados
        for (var entry : channelMappings) {
            if (deleteChannelQuietly(guild.getGuildChannelById(entry.discordId()), entry.displayName(), kept)) channels++;
        }

        // 2. Deletar categorias mapeadas (e o que sobrou dentro delas)
        for (var entry : catMappings) {
            Category cat = guild.getCategoryById(entry.discordId());
            if (cat == null) continue;
            for (GuildChannel child : cat.getChannels()) {
                if (deleteChannelQuietly(child, child.getName(), kept)) channels++;
            }
            if (deleteChannelQuietly(cat, entry.displayName(), kept)) channels++;
        }

        // 3. Deletar cargos gerenciados (exceto @everyone e cargos protegidos pelo Discord)
        for (var entry : roleMappings) {
            Role role = guild.getRoleById(entry.discordId());
            if (role != null && deleteRoleQuietly(guild, role, kept)) roles++;
        }

        // 4. Limpar banco de dados para a guilda
        repository.deleteGuildMappings(guild.getId());
        LOGGER.info("Reset do servidor {} finalizado com sucesso!", guild.getName());
        return new WipeResult(channels, roles, kept);
    }

    /**
     * Apaga TODOS os canais e cargos do servidor que o bot tem permissão para apagar.
     * Mantém apenas o que o Discord não permite remover: @everyone, cargos de integrações/boost,
     * cargos acima do bot e os canais obrigatórios de servidores com Comunidade.
     */
    private WipeResult doWipeAll(Guild guild) {
        LOGGER.warn("Iniciando limpeza COMPLETA do servidor {}", guild.getName());
        int channels = 0;
        int roles = 0;
        List<String> kept = new ArrayList<>();

        Set<String> protectedChannels = new HashSet<>();
        if (guild.getRulesChannel() != null) protectedChannels.add(guild.getRulesChannel().getId());
        if (guild.getCommunityUpdatesChannel() != null) protectedChannels.add(guild.getCommunityUpdatesChannel().getId());

        // 1. Canais (antes das categorias, para não deixar órfãos)
        List<GuildChannel> all = new ArrayList<>(guild.getChannels());
        all.sort(Comparator.comparing(ch -> ch instanceof Category));
        for (GuildChannel channel : all) {
            if (protectedChannels.contains(channel.getId())) {
                kept.add("#" + channel.getName() + " (exigido pela Comunidade)");
                continue;
            }
            if (channel instanceof Category cat && cat.getChannels().stream().anyMatch(ch -> protectedChannels.contains(ch.getId()))) {
                kept.add(channel.getName() + " (contém canal da Comunidade)");
                continue;
            }
            if (deleteChannelQuietly(channel, channel.getName(), kept)) channels++;
        }

        // 2. Cargos
        for (Role role : new ArrayList<>(guild.getRoles())) {
            if (role.isPublicRole()) continue;
            if (role.isManaged()) {
                kept.add("@" + role.getName() + " (gerenciado por integração/boost)");
                continue;
            }
            if (deleteRoleQuietly(guild, role, kept)) roles++;
        }

        repository.deleteGuildMappings(guild.getId());
        LOGGER.warn("Limpeza completa de {} finalizada: {} canais e {} cargos apagados", guild.getName(), channels, roles);
        return new WipeResult(channels, roles, kept);
    }

    private boolean deleteChannelQuietly(GuildChannel channel, String displayName, List<String> kept) {
        if (channel == null) return false;
        try {
            channel.delete().complete();
            LOGGER.debug("Canal deletado: {}", displayName);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Não foi possível deletar canal {}: {}", displayName, e.getMessage());
            kept.add("#" + displayName + " (" + e.getMessage() + ")");
            return false;
        }
    }

    private boolean deleteRoleQuietly(Guild guild, Role role, List<String> kept) {
        if (role.isPublicRole() || role.isManaged()) return false;
        if (!guild.getSelfMember().canInteract(role)) {
            kept.add("@" + role.getName() + " (acima do cargo do bot)");
            return false;
        }
        try {
            role.delete().complete();
            LOGGER.debug("Cargo deletado: {}", role.getName());
            return true;
        } catch (Exception e) {
            LOGGER.warn("Não foi possível deletar cargo {}: {}", role.getName(), e.getMessage());
            kept.add("@" + role.getName() + " (" + e.getMessage() + ")");
            return false;
        }
    }

    // ---------------------------------------------------------------- utilitários

    private <T> Optional<T> findMapped(Guild guild, String type, String key, java.util.function.Function<String, T> resolver) {
        return repository.getDiscordId(guild.getId(), type, key).map(resolver);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
