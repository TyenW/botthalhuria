package com.thalhuria.bot.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@JsonIgnoreProperties({"$schema"})
public class ServerTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTemplate.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern KEY_PLACEHOLDER = Pattern.compile("\\{(channel|role):([A-Za-z0-9_]+)}");

    private String templateName = "Servidor Padrão Thalhuria";
    private String version = "1.0.0";
    private List<RoleTemplate> roles = new ArrayList<>();
    private List<CategoryTemplate> categories = new ArrayList<>();
    private BrandingConfig branding = new BrandingConfig();
    private OnboardingConfig onboarding = new OnboardingConfig();
    private TicketConfig tickets = new TicketConfig();
    private LevelingConfig leveling = new LevelingConfig();
    private EconomyConfig economy = new EconomyConfig();
    private ModerationConfig moderation = new ModerationConfig();
    private WelcomeCardConfig welcomeCard = new WelcomeCardConfig();
    private EventsConfig events = new EventsConfig();
    private InvitesConfig invites = new InvitesConfig();
    private CommandsConfig commands = new CommandsConfig();
    private java.util.Map<String, String> messages = new java.util.HashMap<>();

    public ServerTemplate() {}

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<RoleTemplate> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleTemplate> roles) {
        this.roles = roles;
    }

    public List<CategoryTemplate> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryTemplate> categories) {
        this.categories = categories;
    }

    public BrandingConfig getBranding() {
        return branding;
    }

    public void setBranding(BrandingConfig branding) {
        this.branding = branding != null ? branding : new BrandingConfig();
    }

    public OnboardingConfig getOnboarding() {
        return onboarding;
    }

    public void setOnboarding(OnboardingConfig onboarding) {
        this.onboarding = onboarding != null ? onboarding : new OnboardingConfig();
    }

    public TicketConfig getTickets() {
        return tickets;
    }

    public void setTickets(TicketConfig tickets) {
        this.tickets = tickets != null ? tickets : new TicketConfig();
    }

    public LevelingConfig getLeveling() {
        return leveling;
    }

    public void setLeveling(LevelingConfig leveling) {
        this.leveling = leveling != null ? leveling : new LevelingConfig();
    }

    public EconomyConfig getEconomy() {
        return economy;
    }

    public void setEconomy(EconomyConfig economy) {
        this.economy = economy != null ? economy : new EconomyConfig();
    }

    public ModerationConfig getModeration() {
        return moderation;
    }

    public void setModeration(ModerationConfig moderation) {
        this.moderation = moderation != null ? moderation : new ModerationConfig();
    }

    public WelcomeCardConfig getWelcomeCard() {
        return welcomeCard;
    }

    public void setWelcomeCard(WelcomeCardConfig welcomeCard) {
        this.welcomeCard = welcomeCard != null ? welcomeCard : new WelcomeCardConfig();
    }

    public EventsConfig getEvents() {
        return events;
    }

    public void setEvents(EventsConfig events) {
        this.events = events != null ? events : new EventsConfig();
    }

    public java.util.Map<String, String> getMessages() {
        return messages;
    }

    public void setMessages(java.util.Map<String, String> messages) {
        this.messages = messages != null ? messages : new java.util.HashMap<>();
    }

    public InvitesConfig getInvites() {
        return invites;
    }

    public void setInvites(InvitesConfig invites) {
        this.invites = invites != null ? invites : new InvitesConfig();
    }

    public CommandsConfig getCommands() {
        return commands;
    }

    public void setCommands(CommandsConfig commands) {
        this.commands = commands != null ? commands : new CommandsConfig();
    }

    public Stream<ChannelTemplate> allChannels() {
        return categories.stream().flatMap(cat -> cat.getChannels().stream());
    }

    public Optional<RoleTemplate> findRole(String key) {
        return roles.stream().filter(r -> r.getKey() != null && r.getKey().equalsIgnoreCase(key)).findFirst();
    }

    public Optional<ChannelTemplate> findChannel(String key) {
        return allChannels().filter(c -> c.getKey() != null && c.getKey().equalsIgnoreCase(key)).findFirst();
    }

    public List<RoleTemplate> selfAssignableRoles() {
        return roles.stream().filter(RoleTemplate::isSelfAssignable).toList();
    }

    /**
     * Verifica a consistência do template (chaves duplicadas, referências quebradas, permissões inválidas,
     * limites do Discord). Retorna a lista de problemas encontrados; vazia significa template válido.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        Set<String> roleKeys = new HashSet<>();
        Set<String> roleNames = new HashSet<>();
        Set<String> categoryKeys = new HashSet<>();
        Set<String> channelKeys = new HashSet<>();

        for (RoleTemplate role : roles) {
            if (isBlank(role.getKey()) || isBlank(role.getName())) {
                problems.add("Cargo sem key ou name: " + role.getName());
                continue;
            }
            if (!roleKeys.add(role.getKey().toUpperCase())) problems.add("Key de cargo duplicada: " + role.getKey());
            roleNames.add(role.getName().toLowerCase());
            checkLength(problems, "Nome do cargo " + role.getKey(), role.getName(), 100);
            checkColor(problems, "Cor do cargo " + role.getKey(), role.getColorHex());
            checkPermissions(problems, "Cargo " + role.getKey(), role.getPermissions());
        }

        int logChannels = 0;
        int roomCategories = 0;
        for (CategoryTemplate cat : categories) {
            if (isBlank(cat.getKey()) || isBlank(cat.getName())) {
                problems.add("Categoria sem key ou name: " + cat.getName());
                continue;
            }
            if (!categoryKeys.add(cat.getKey().toUpperCase())) problems.add("Key de categoria duplicada: " + cat.getKey());
            if (cat.isIndividualRoomsCategory()) roomCategories++;
            checkLength(problems, "Nome da categoria " + cat.getKey(), cat.getName(), 100);
            checkRules(problems, "Categoria " + cat.getKey(), cat.getPermissionOverrides(), roleKeys, roleNames);
            if (cat.getChannels().size() > 50) problems.add("Categoria " + cat.getKey() + " excede 50 canais");

            for (ChannelTemplate chan : cat.getChannels()) {
                if (isBlank(chan.getKey()) || isBlank(chan.getName())) {
                    problems.add("Canal sem key ou name em " + cat.getKey());
                    continue;
                }
                String where = "Canal " + chan.getKey();
                if (!channelKeys.add(chan.getKey().toUpperCase())) problems.add("Key de canal duplicada: " + chan.getKey());
                if (chan.isSystemLogChannel()) logChannels++;
                checkLength(problems, "Nome do " + where, chan.getName(), 100);
                checkLength(problems, "Tópico do " + where, chan.getTopic(), 1024);
                checkRules(problems, where, chan.getPermissionOverrides(), roleKeys, roleNames);

                String type = chan.getType() == null ? "TEXT" : chan.getType().toUpperCase();
                if (!List.of("TEXT", "VOICE", "ANNOUNCEMENT", "NEWS").contains(type)) {
                    problems.add(where + ": tipo desconhecido '" + chan.getType() + "'");
                }
                if (chan.getSlowmode() < 0 || chan.getSlowmode() > 21600) problems.add(where + ": slowmode deve estar entre 0 e 21600");
                if (chan.getUserLimit() < 0 || chan.getUserLimit() > 99) problems.add(where + ": userLimit deve estar entre 0 e 99");
                if (chan.getTempUserLimit() < 0 || chan.getTempUserLimit() > 99) problems.add(where + ": tempUserLimit deve estar entre 0 e 99");
                if (chan.isJoinToCreate() && !"VOICE".equals(type)) problems.add(where + ": joinToCreate exige um canal VOICE");
                if (chan.getPanel() != null && "VOICE".equals(type)) problems.add(where + ": painéis só podem ser publicados em canais de texto");
                if (chan.getPanel() != null) checkPanel(problems, where, chan.getPanel());
                if (chan.getCounter() != null) {
                    if (!List.of("MEMBERS", "HUMANS", "BOTS", "ONLINE", "BOOSTS").contains(chan.getCounter().toUpperCase())) {
                        problems.add(where + ": counter inválido '" + chan.getCounter() + "'");
                    }
                    if (!"VOICE".equals(type)) problems.add(where + ": contadores devem ser canais VOICE");
                    if (!chan.getName().contains("{value}")) problems.add(where + ": o nome de um contador precisa conter {value}");
                }
            }
        }

        if (logChannels != 1) problems.add("O template deve ter exatamente 1 canal com systemLogChannel=true (encontrados: " + logChannels + ")");
        if (roomCategories > 1) problems.add("Apenas 1 categoria pode ter individualRoomsCategory=true");
        if (selfAssignableRoles().size() > 25) problems.add("Máximo de 25 cargos selfAssignable (limite do menu do Discord)");

        if (onboarding.isEnabled()) {
            checkRef(problems, "onboarding.autoRoleKey", onboarding.getAutoRoleKey(), roleKeys);
            checkRef(problems, "onboarding.botRoleKey", onboarding.getBotRoleKey(), roleKeys);
            checkRef(problems, "onboarding.verifiedRoleKey", onboarding.getVerifiedRoleKey(), roleKeys);
            checkRef(problems, "onboarding.welcomeChannelKey", onboarding.getWelcomeChannelKey(), channelKeys);
            checkRef(problems, "onboarding.rulesChannelKey", onboarding.getRulesChannelKey(), channelKeys);
            checkRef(problems, "onboarding.dmButtonChannelKey", onboarding.getDmButtonChannelKey(), channelKeys);
            checkRef(problems, "onboarding.communityUpdatesChannelKey", onboarding.getCommunityUpdatesChannelKey(), channelKeys);
        }
        if (tickets.isEnabled()) {
            checkRef(problems, "tickets.categoryKey", tickets.getCategoryKey(), categoryKeys);
            for (String key : tickets.getStaffRoleKeys()) checkRef(problems, "tickets.staffRoleKeys", key, roleKeys);
        }
        checkColor(problems, "branding.colorHex", branding.getColorHex());

        Set<String> ticketTypes = new HashSet<>();
        for (TicketType type : tickets.getTypes()) {
            if (isBlank(type.getKey()) || isBlank(type.getLabel())) problems.add("tickets.types: tipo sem key ou label");
            else if (!ticketTypes.add(type.getKey().toLowerCase())) problems.add("tickets.types: key repetida '" + type.getKey() + "'");
            for (String key : type.getStaffRoleKeys()) checkRef(problems, "tickets.types[" + type.getKey() + "].staffRoleKeys", key, roleKeys);
        }

        for (ChannelTemplate chan : allChannels().toList()) {
            if (chan.getPanel() == null) continue;
            for (PanelButton button : chan.getPanel().getButtons()) {
                String action = button.getAction() == null ? "" : button.getAction().toUpperCase();
                if ("TICKET".equals(action) && !isBlank(button.getTicketType())
                        && !ticketTypes.contains(button.getTicketType().toLowerCase())) {
                    problems.add("Canal " + chan.getKey() + ": botão aponta para tipo de ticket inexistente '" + button.getTicketType() + "'");
                }
                if ("ROLE".equals(action)) checkRef(problems, "Canal " + chan.getKey() + " botão ROLE", button.getRoleKey(), roleKeys);
            }
        }

        long owners = roles.stream().filter(RoleTemplate::isOwnerRole).count();
        if (owners > 1) problems.add("Apenas 1 cargo pode ter ownerRole=true");

        if (leveling.isEnabled()) {
            checkRef(problems, "leveling.announceChannelKey", leveling.getAnnounceChannelKey(), channelKeys);
            if (leveling.getXpMin() < 0 || leveling.getXpMax() < leveling.getXpMin()) problems.add("leveling: xpMax deve ser >= xpMin >= 0");
            for (LevelReward reward : leveling.getRewards()) {
                checkRef(problems, "leveling.rewards[nível " + reward.getLevel() + "]", reward.getRoleKey(), roleKeys);
                if (reward.getLevel() < 1) problems.add("leveling.rewards: nível deve ser >= 1");
            }
            for (String key : leveling.getIgnoredChannelKeys()) checkRef(problems, "leveling.ignoredChannelKeys", key, channelKeys);
        }

        if (economy.isEnabled()) {
            Set<String> itemKeys = new HashSet<>();
            for (ShopItem item : economy.getShop()) {
                if (isBlank(item.getKey()) || isBlank(item.getName())) problems.add("economy.shop: item sem key ou name");
                else if (!itemKeys.add(item.getKey().toUpperCase())) problems.add("economy.shop: key duplicada " + item.getKey());
                checkRef(problems, "economy.shop[" + item.getKey() + "].roleKey", item.getRoleKey(), roleKeys);
                if (item.getPrice() < 0) problems.add("economy.shop[" + item.getKey() + "]: preço negativo");
            }
            if (economy.getShop().size() > 25) problems.add("economy.shop: máximo de 25 itens (limite do menu)");
        }

        checkRef(problems, "moderation.modLogChannelKey", moderation.getModLogChannelKey(), channelKeys);
        for (WarnThreshold t : moderation.getWarnThresholds()) {
            if (!List.of("TIMEOUT", "KICK", "BAN").contains(String.valueOf(t.getAction()).toUpperCase())) {
                problems.add("moderation.warnThresholds: ação inválida '" + t.getAction() + "' (use TIMEOUT, KICK ou BAN)");
            }
            if ("TIMEOUT".equalsIgnoreCase(t.getAction()) && (t.getDurationMinutes() < 1 || t.getDurationMinutes() > 40320)) {
                problems.add("moderation.warnThresholds: castigo deve durar entre 1 minuto e 28 dias");
            }
        }
        for (String key : moderation.getAutomod().getExemptRoleKeys()) checkRef(problems, "moderation.automod.exemptRoleKeys", key, roleKeys);
        if (moderation.getAutomod().getMentionLimit() > 50) problems.add("moderation.automod.mentionLimit: máximo 50");

        if (events.isEnabled()) {
            checkRef(problems, "events.announceChannelKey", events.getAnnounceChannelKey(), channelKeys);
            checkRef(problems, "events.pingRoleKey", events.getPingRoleKey(), roleKeys);
        }
        if (invites.isEnabled()) {
            Set<Integer> tierCounts = new HashSet<>();
            for (InviteTier tier : invites.getTiers()) {
                checkRef(problems, "invites.tiers[" + tier.getInvites() + "].roleKey", tier.getRoleKey(), roleKeys);
                if (tier.getInvites() < 1) problems.add("invites.tiers: quantidade de convites deve ser >= 1");
                if (!tierCounts.add(tier.getInvites())) problems.add("invites.tiers: quantidade repetida " + tier.getInvites());
            }
            if (invites.getCoinsPerInvite() < 0) problems.add("invites.coinsPerInvite não pode ser negativo");
        }
        for (String key : commands.getAllowedChannelKeys()) checkRef(problems, "commands.allowedChannelKeys", key, channelKeys);
        for (String key : commands.getBypassRoleKeys()) checkRef(problems, "commands.bypassRoleKeys", key, roleKeys);
        checkColor(problems, "welcomeCard.backgroundColorHex", welcomeCard.getBackgroundColorHex());
        checkColor(problems, "welcomeCard.accentColorHex", welcomeCard.getAccentColorHex());

        // Placeholders {channel:KEY} e {role:KEY} usados nos textos precisam existir
        Stream.concat(
                Stream.concat(allChannels().map(ChannelTemplate::getTopic),
                        allChannels().map(ChannelTemplate::getPanel).filter(Objects::nonNull).flatMap(ServerTemplate::panelTexts)),
                Stream.concat(
                        Stream.of(onboarding.getWelcomeTitle(), onboarding.getWelcomeMessage(), onboarding.getVerifiedMessage(),
                                onboarding.getDmTitle(), onboarding.getDmMessage(), tickets.getOpenTitle(), tickets.getOpenMessage(),
                                leveling.getLevelUpMessage(), welcomeCard.getTitle(), welcomeCard.getSubtitle()),
                        tickets.getTypes().stream().map(TicketType::getOpenMessage))
        ).filter(Objects::nonNull).forEach(text -> {
            Matcher matcher = KEY_PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                Set<String> pool = matcher.group(1).equals("channel") ? channelKeys : roleKeys;
                if (!pool.contains(matcher.group(2).toUpperCase())) {
                    problems.add("Placeholder aponta para key inexistente: " + matcher.group());
                }
            }
        });

        return problems;
    }

    private static Stream<String> panelTexts(PanelTemplate panel) {
        return Stream.concat(
                Stream.of(panel.getTitle(), panel.getDescription()),
                panel.getFields().stream().flatMap(f -> Stream.of(f.getName(), f.getValue()))
        );
    }

    private static void checkPanel(List<String> problems, String where, PanelTemplate panel) {
        String type = panel.getType() == null ? "EMBED" : panel.getType().toUpperCase();
        if (!List.of("EMBED", "VERIFY", "SELF_ROLES", "TICKET").contains(type)) {
            problems.add(where + ": painel com tipo desconhecido '" + panel.getType() + "'");
        }
        checkLength(problems, where + " título do painel", panel.getTitle(), 256);
        checkLength(problems, where + " descrição do painel", panel.getDescription(), 4096);
        checkLength(problems, where + " texto do botão", panel.getButtonLabel(), 80);
        checkLength(problems, where + " placeholder do menu", panel.getPlaceholder(), 150);
        checkColor(problems, where + " cor do painel", panel.getColorHex());
        if (panel.getFields().size() > 25) problems.add(where + ": painel excede 25 fields");
        int total = panelTexts(panel).filter(Objects::nonNull).mapToInt(String::length).sum();
        if (total > 5500) problems.add(where + ": painel com " + total + " caracteres (o Discord aceita até 6000 por embed, contando o rodapé)");
        if (panel.getButtons().size() > 5) problems.add(where + ": máximo de 5 botões por painel");
        for (PanelButton button : panel.getButtons()) {
            String action = button.getAction() == null ? "" : button.getAction().toUpperCase();
            if (!List.of("TICKET", "VERIFY", "ROLE", "URL").contains(action)) problems.add(where + ": botão com ação inválida '" + button.getAction() + "'");
            if ("URL".equals(action) && isBlank(button.getUrl())) problems.add(where + ": botão URL sem url");
            if ("ROLE".equals(action) && isBlank(button.getRoleKey())) problems.add(where + ": botão ROLE sem roleKey");
            if (isBlank(button.getLabel()) && isBlank(button.getEmoji())) problems.add(where + ": botão sem label nem emoji");
            checkLength(problems, where + " texto do botão", button.getLabel(), 80);
        }
        for (PanelTemplate.Field field : panel.getFields()) {
            if (isBlank(field.getName()) || isBlank(field.getValue())) problems.add(where + ": field de painel sem name/value");
            checkLength(problems, where + " nome do field", field.getName(), 256);
            checkLength(problems, where + " field '" + field.getName() + "'", field.getValue(), 1024);
        }
    }

    private static void checkRules(List<String> problems, String where, List<PermissionRule> rules, Set<String> roleKeys, Set<String> roleNames) {
        for (PermissionRule rule : rules) {
            String target = rule.getTarget();
            boolean known = target != null && (target.equalsIgnoreCase("@everyone") || target.equalsIgnoreCase("everyone")
                    || roleKeys.contains(target.toUpperCase()) || roleNames.contains(target.toLowerCase()));
            if (!known) problems.add(where + ": alvo de permissão desconhecido '" + target + "'");
            checkPermissions(problems, where, rule.getAllow());
            checkPermissions(problems, where, rule.getDeny());
        }
    }

    private static void checkPermissions(List<String> problems, String where, List<String> perms) {
        if (perms == null) return;
        for (String perm : perms) {
            if (PermissionParser.parse(perm).isEmpty()) problems.add(where + ": permissão inválida '" + perm + "'");
        }
    }

    private static void checkRef(List<String> problems, String field, String key, Set<String> pool) {
        if (!isBlank(key) && !pool.contains(key.toUpperCase())) problems.add(field + " aponta para key inexistente: " + key);
    }

    private static void checkLength(List<String> problems, String what, String value, int max) {
        if (value != null && value.length() > max) problems.add(what + " excede " + max + " caracteres");
    }

    private static void checkColor(List<String> problems, String what, String hex) {
        if (isBlank(hex)) return;
        if (!hex.matches("#?[0-9A-Fa-f]{6}")) problems.add(what + " inválida: " + hex);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Carrega template de arquivo JSON externo ou, caso não exista, do recurso embutido,
     * ou cria o template padrão programmaticamente.
     */
    public static ServerTemplate load(String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            try {
                LOGGER.info("Carregando template de servidor a partir do arquivo: {}", file.getAbsolutePath());
                return MAPPER.readValue(file, ServerTemplate.class);
            } catch (Exception e) {
                // Não cair silenciosamente no template embutido: o admin aplicaria a estrutura errada sem perceber
                LOGGER.error("Falha ao ler template de {}", filePath, e);
                throw new IllegalStateException("Template inválido em " + filePath + ": " + e.getMessage(), e);
            }
        }

        try (InputStream is = ServerTemplate.class.getResourceAsStream("/default-server-template.json")) {
            if (is != null) {
                LOGGER.info("Carregando template padrão embutido em resources");
                return MAPPER.readValue(is, ServerTemplate.class);
            }
        } catch (Exception e) {
            LOGGER.warn("Recurso default-server-template.json não disponível: {}", e.getMessage());
        }

        LOGGER.info("Construindo template padrão via código");
        return createDefault();
    }

    /**
     * Cria a especificação completa de cargos, categorias, canais e regras de permissão
     * conforme solicitado na especificação do projeto.
     */
    public static ServerTemplate createDefault() {
        ServerTemplate template = new ServerTemplate();
        template.setTemplateName("Estrutura Completa Thalhuria");
        template.setVersion("1.0.0");

        // 1. CARGOS BASE (Roles)
        RoleTemplate adminRole = new RoleTemplate("ROLE_ADMIN", "Admin", "#E74C3C", true, true,
                List.of("ADMINISTRATOR"));
        adminRole.setAdminRole(true);

        RoleTemplate modRole = new RoleTemplate("ROLE_MODERATOR", "Moderador", "#3498DB", true, true,
                List.of("KICK_MEMBERS", "BAN_MEMBERS", "MANAGE_MESSAGES", "MUTE_MEMBERS", "DEAFEN_MEMBERS", "MOVE_MEMBERS", "VIEW_AUDIT_LOGS"));

        RoleTemplate botRole = new RoleTemplate("ROLE_BOT", "Bot", "#9B59B6", true, false,
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_EMBED_LINKS", "MESSAGE_ATTACH_FILES", "MESSAGE_HISTORY", "USE_APPLICATION_COMMANDS"));

        RoleTemplate memberRole = new RoleTemplate("ROLE_MEMBER", "Membro", "#2ECC71", true, false,
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MESSAGE_ADD_REACTION", "VOICE_CONNECT", "VOICE_SPEAK", "VOICE_STREAM"));
        memberRole.setMemberRole(true);

        RoleTemplate visitorRole = new RoleTemplate("ROLE_VISITOR", "Visitante", "#95A5A6", false, false,
                List.of("VIEW_CHANNEL", "MESSAGE_HISTORY"));

        template.setRoles(Arrays.asList(adminRole, modRole, botRole, memberRole, visitorRole));

        // 2. CATEGORIAS & CANAIS
        List<CategoryTemplate> categories = new ArrayList<>();

        // Categoria 1: 📌 Informações
        CategoryTemplate infoCat = new CategoryTemplate("CAT_INFO", "📌 Informações", 1);
        infoCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of("VIEW_CHANNEL", "MESSAGE_HISTORY"),
                List.of("MESSAGE_SEND", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "MESSAGE_ADD_REACTION")));
        infoCat.getPermissionOverrides().add(new PermissionRule("Admin",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MANAGE_MESSAGES"),
                List.of()));

        ChannelTemplate regras = new ChannelTemplate("CHAN_REGRAS", "regras", "TEXT", "Regras e diretrizes oficiais da comunidade");
        ChannelTemplate avisos = new ChannelTemplate("CHAN_AVISOS", "avisos", "ANNOUNCEMENT", "Comunicados e anúncios importantes");
        ChannelTemplate faq = new ChannelTemplate("CHAN_FAQ", "faq-duvidas", "TEXT", "Perguntas frequentes e suporte básico");
        infoCat.setChannels(Arrays.asList(regras, avisos, faq));
        categories.add(infoCat);

        // Categoria 2: 💬 Geral
        CategoryTemplate geralCat = new CategoryTemplate("CAT_GENERAL", "💬 Geral", 2);
        geralCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of(),
                List.of("VIEW_CHANNEL", "MESSAGE_SEND")));
        geralCat.getPermissionOverrides().add(new PermissionRule("Membro",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MESSAGE_ATTACH_FILES", "MESSAGE_EMBED_LINKS"),
                List.of()));
        geralCat.getPermissionOverrides().add(new PermissionRule("Admin",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MANAGE_MESSAGES"),
                List.of()));

        ChannelTemplate chat = new ChannelTemplate("CHAN_CHAT", "bate-papo", "TEXT", "Converse sobre qualquer assunto com a comunidade");
        ChannelTemplate comandos = new ChannelTemplate("CHAN_COMMANDS", "comandos", "TEXT", "Canal dedicado a comandos de bots");
        ChannelTemplate midia = new ChannelTemplate("CHAN_MEDIA", "midia-e-artes", "TEXT", "Compartilhe imagens, vídeos e projetos");
        geralCat.setChannels(Arrays.asList(chat, comandos, midia));
        categories.add(geralCat);

        // Categoria 3: 🔊 Canais de Voz
        CategoryTemplate voiceCat = new CategoryTemplate("CAT_VOICE", "🔊 Canais de Voz", 3);
        voiceCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of(),
                List.of("VOICE_CONNECT")));
        voiceCat.getPermissionOverrides().add(new PermissionRule("Membro",
                List.of("VIEW_CHANNEL", "VOICE_CONNECT", "VOICE_SPEAK", "VOICE_STREAM"),
                List.of()));

        ChannelTemplate voz1 = new ChannelTemplate("CHAN_VOICE_1", "Geral 01", "VOICE", "Canal de voz principal");
        ChannelTemplate voz2 = new ChannelTemplate("CHAN_VOICE_2", "Geral 02", "VOICE", "Canal de voz secundário");
        ChannelTemplate vozMusica = new ChannelTemplate("CHAN_VOICE_MUSIC", "Música & Lounge", "VOICE", "Música e bate-papo descontraído");
        voiceCat.setChannels(Arrays.asList(voz1, voz2, vozMusica));
        categories.add(voiceCat);

        // Categoria 4: 🔒 Área Restrita
        CategoryTemplate adminCat = new CategoryTemplate("CAT_ADMIN", "🔒 Área Restrita", 4);
        adminCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of(),
                List.of("VIEW_CHANNEL")));
        adminCat.getPermissionOverrides().add(new PermissionRule("Admin",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MANAGE_CHANNELS", "MANAGE_MESSAGES"),
                List.of()));
        adminCat.getPermissionOverrides().add(new PermissionRule("Moderador",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY"),
                List.of()));

        ChannelTemplate painelAdmin = new ChannelTemplate("CHAN_ADMIN_PANEL", "painel-admin", "TEXT", "Discussões da administração e decisões");
        ChannelTemplate modChat = new ChannelTemplate("CHAN_MOD_CHAT", "chat-moderacao", "TEXT", "Coordenação da equipe de moderação");
        adminCat.setChannels(Arrays.asList(painelAdmin, modChat));
        categories.add(adminCat);

        // Categoria 5: ⚙️ Logs do Sistema
        CategoryTemplate systemCat = new CategoryTemplate("CAT_SYSTEM", "⚙️ Logs do Sistema", 5);
        systemCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of(),
                List.of("VIEW_CHANNEL")));
        systemCat.getPermissionOverrides().add(new PermissionRule("Admin",
                List.of("VIEW_CHANNEL", "MESSAGE_HISTORY"),
                List.of("MESSAGE_SEND"))); // Somente leitura para admins humanos, bot tem permissão global de envio

        ChannelTemplate logsChannel = new ChannelTemplate("CHAN_LOGS", "logs-do-sistema", "TEXT", "Registro automatizado de auditoria e eventos do bot");
        logsChannel.setSystemLogChannel(true);
        systemCat.setChannels(List.of(logsChannel));
        categories.add(systemCat);

        // Categoria 6: 👤 Salas Individuais (para gerenciamento de membros e salas privadas)
        CategoryTemplate privateCat = new CategoryTemplate("CAT_INDIVIDUAL_ROOMS", "👤 Salas Individuais", 6);
        privateCat.setIndividualRoomsCategory(true);
        privateCat.getPermissionOverrides().add(new PermissionRule("@everyone",
                List.of(),
                List.of("VIEW_CHANNEL")));
        privateCat.getPermissionOverrides().add(new PermissionRule("Admin",
                List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_HISTORY", "MANAGE_CHANNELS"),
                List.of()));
        categories.add(privateCat);

        template.setCategories(categories);
        return template;
    }
}
