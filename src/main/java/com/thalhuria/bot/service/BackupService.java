package com.thalhuria.bot.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thalhuria.bot.config.*;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exporta o estado atual do servidor (cargos, categorias, canais e permissões) como um template.
 *
 * Itens criados pelo bot mantêm a key original; os demais recebem uma key gerada a partir do nome.
 * Seções de configuração (branding, onboarding, painéis...) são copiadas do template ativo, então o arquivo
 * exportado é um template completo: copie para templates/&lt;ID_DO_SERVIDOR&gt;.json e rode /setup-server para restaurar.
 */
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    private final ServerMapRepository repository;
    private final TemplateService templateService;
    private final String backupsDir;

    public record Backup(File file, byte[] json, int roles, int categories, int channels) {}

    public BackupService(ServerMapRepository repository, TemplateService templateService, String backupsDir) {
        this.repository = repository;
        this.templateService = templateService;
        this.backupsDir = backupsDir;
    }

    public Backup export(Guild guild) throws Exception {
        // Cópia profunda: o backup ajusta nomes/permissões e não pode alterar o template em uso
        ServerTemplate active = MAPPER.convertValue(templateService.get(guild), ServerTemplate.class);
        ServerTemplate out = new ServerTemplate();
        out.setTemplateName(guild.getName() + " • backup");
        out.setVersion(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HHmm")));
        out.setBranding(active.getBranding());
        out.setOnboarding(active.getOnboarding());
        out.setTickets(active.getTickets());
        out.setLeveling(active.getLeveling());
        out.setEconomy(active.getEconomy());
        out.setModeration(active.getModeration());
        out.setWelcomeCard(active.getWelcomeCard());
        out.setEvents(active.getEvents());
        out.setMessages(active.getMessages());

        Map<String, String> knownKeys = new HashMap<>();
        for (String type : List.of("ROLE", "CATEGORY", "CHANNEL")) {
            repository.getMappingsByType(guild.getId(), type).forEach(e -> knownKeys.put(e.discordId(), e.keyName()));
        }
        Set<String> usedKeys = new HashSet<>(knownKeys.values());

        // Cargos (ordem = hierarquia)
        Map<Long, String> roleKeys = new HashMap<>();
        List<RoleTemplate> roles = new ArrayList<>();
        for (Role role : guild.getRoles()) {
            if (role.isPublicRole() || role.isManaged()) continue;
            String key = knownKeys.getOrDefault(role.getId(), uniqueKey("ROLE_", role.getName(), usedKeys));
            roleKeys.put(role.getIdLong(), key);
            RoleTemplate tpl = active.findRole(key).orElseGet(RoleTemplate::new);
            tpl.setKey(key);
            tpl.setName(role.getName());
            tpl.setColorHex(role.getColorRaw() == Role.DEFAULT_COLOR_RAW ? null : String.format("#%06X", role.getColorRaw()));
            tpl.setHoist(role.isHoisted());
            tpl.setMentionable(role.isMentionable());
            tpl.setPermissions(role.getPermissions().stream().map(Permission::name).sorted().toList());
            roles.add(tpl);
        }
        out.setRoles(roles);

        // Categorias e canais
        List<CategoryTemplate> categories = new ArrayList<>();
        int channelCount = 0;
        int position = 1;
        for (Category cat : guild.getCategories()) {
            String key = knownKeys.getOrDefault(cat.getId(), uniqueKey("CAT_", cat.getName(), usedKeys));
            CategoryTemplate tpl = new CategoryTemplate(key, cat.getName(), position++);
            active.getCategories().stream().filter(c -> c.getKey().equalsIgnoreCase(key)).findFirst()
                    .ifPresent(orig -> tpl.setIndividualRoomsCategory(orig.isIndividualRoomsCategory()));
            tpl.setPermissionOverrides(rules(cat, roleKeys, null));
            List<ChannelTemplate> channels = new ArrayList<>();
            for (GuildChannel ch : cat.getChannels()) {
                if (isTemporary(guild, ch)) continue;
                ChannelTemplate chanTpl = channel(guild, ch, cat, roleKeys, knownKeys, usedKeys, active);
                if (chanTpl != null) channels.add(chanTpl);
            }
            tpl.setChannels(channels);
            channelCount += channels.size();
            categories.add(tpl);
        }

        // Canais sem categoria vão para uma categoria própria
        List<ChannelTemplate> loose = new ArrayList<>();
        for (GuildChannel ch : guild.getChannels()) {
            if (ch instanceof Category) continue;
            if (ch instanceof StandardGuildChannel sgc && sgc.getParentCategory() == null && !isTemporary(guild, ch)) {
                ChannelTemplate chanTpl = channel(guild, ch, null, roleKeys, knownKeys, usedKeys, active);
                if (chanTpl != null) loose.add(chanTpl);
            }
        }
        if (!loose.isEmpty()) {
            CategoryTemplate tpl = new CategoryTemplate(uniqueKey("CAT_", "sem categoria", usedKeys), "━━ SEM CATEGORIA ━━", position);
            tpl.setChannels(loose);
            channelCount += loose.size();
            categories.add(tpl);
        }
        out.setCategories(categories);

        // Serializar com $schema na frente para o autocomplete do editor
        ObjectNode node = MAPPER.valueToTree(out);
        ObjectNode ordered = MAPPER.createObjectNode().put("$schema", "../server-template.schema.json");
        ordered.setAll(node);
        byte[] json = MAPPER.writeValueAsString(ordered).getBytes(StandardCharsets.UTF_8);

        File dir = new File(backupsDir);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, guild.getId() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        Files.write(file.toPath(), json);
        LOGGER.info("Backup de {} salvo em {}", guild.getName(), file.getPath());
        return new Backup(file, json, roles.size(), categories.size(), channelCount);
    }

    private ChannelTemplate channel(Guild guild, GuildChannel ch, Category parent, Map<Long, String> roleKeys,
                                    Map<String, String> knownKeys, Set<String> usedKeys, ServerTemplate active) {
        String type;
        if (ch instanceof NewsChannel) type = "ANNOUNCEMENT";
        else if (ch instanceof TextChannel) type = "TEXT";
        else if (ch instanceof VoiceChannel) type = "VOICE";
        else return null; // fóruns, palcos e outros tipos ainda não são suportados pelo template

        String key = knownKeys.getOrDefault(ch.getId(), uniqueKey("CHAN_", ch.getName(), usedKeys));
        ChannelTemplate tpl = active.findChannel(key).orElseGet(ChannelTemplate::new);
        tpl.setKey(key);
        if (tpl.getCounter() == null) tpl.setName(ch.getName());
        tpl.setType(type);
        if (ch instanceof TextChannel tc) {
            tpl.setTopic(tc.getTopic() == null ? "" : tc.getTopic());
            tpl.setSlowmode(tc.getSlowmode());
            tpl.setNsfw(tc.isNSFW());
        } else if (ch instanceof NewsChannel nc) {
            tpl.setTopic(nc.getTopic() == null ? "" : nc.getTopic());
            tpl.setNsfw(nc.isNSFW());
        } else if (ch instanceof VoiceChannel vc) {
            tpl.setUserLimit(vc.getUserLimit());
            tpl.setNsfw(vc.isNSFW());
        }
        // Só regras que diferem da categoria; canal sincronizado não precisa de nenhuma
        boolean synced = ch instanceof StandardGuildChannel sgc && parent != null && sgc.isSynced();
        tpl.setPermissionOverrides(synced ? new ArrayList<>() : rules((IPermissionContainer) ch, roleKeys, parent));
        return tpl;
    }

    private List<PermissionRule> rules(IPermissionContainer container, Map<Long, String> roleKeys, Category parent) {
        List<PermissionRule> rules = new ArrayList<>();
        for (PermissionOverride o : container.getPermissionOverrides()) {
            if (!o.isRoleOverride()) continue; // overrides por membro (tickets, salas) não fazem parte do template
            Role role = o.getRole();
            if (role == null) continue;
            if (parent != null) {
                PermissionOverride inherited = parent.getPermissionOverride(role);
                if (inherited != null && inherited.getAllowedRaw() == o.getAllowedRaw() && inherited.getDeniedRaw() == o.getDeniedRaw()) continue;
            }
            String target = role.isPublicRole() ? "@everyone" : roleKeys.get(role.getIdLong());
            if (target == null) continue;
            rules.add(new PermissionRule(target,
                    o.getAllowed().stream().map(Permission::name).sorted().toList(),
                    o.getDenied().stream().map(Permission::name).sorted().toList()));
        }
        return rules;
    }

    private boolean isTemporary(Guild guild, GuildChannel ch) {
        if (repository.getDiscordId(guild.getId(), "TEMP_VOICE", ch.getId()).isPresent()) return true;
        return ch instanceof TextChannel tc && tc.getTopic() != null && tc.getTopic().startsWith("ticket:");
    }

    private static String uniqueKey(String prefix, String name, Set<String> used) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) slug = "ITEM";
        String key = prefix + slug;
        int i = 2;
        while (used.contains(key)) key = prefix + slug + "_" + i++;
        used.add(key);
        return key;
    }
}
