package com.thalhuria.bot.service;

import com.thalhuria.bot.config.BrandingConfig;
import com.thalhuria.bot.config.ChannelTemplate;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.database.ServerMapRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ponto único de acesso ao template ativo de cada servidor e aos recursos mapeados no Discord.
 *
 * Template por servidor: se existir {@code templates/<guildId>.json}, ele é usado para aquela guilda;
 * caso contrário vale o template padrão ({@code TEMPLATE_PATH}).
 *
 * Mensagens: os textos fixos do bot ficam em {@code messages.properties} e podem ser sobrescritos
 * pela seção {@code "messages"} do template.
 */
public class TemplateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateService.class);
    private static final Pattern KEY_PLACEHOLDER = Pattern.compile("\\{(channel|role):([A-Za-z0-9_]+)}");

    private final String templatePath;
    private final String templatesDir;
    private final ServerMapRepository repository;
    private final Properties defaultMessages = loadDefaultMessages();
    private volatile ServerTemplate defaultTemplate;
    private final Map<String, ServerTemplate> guildTemplates = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ChannelTemplate>> channelCache = new ConcurrentHashMap<>();

    public TemplateService(String templatePath, String templatesDir, ServerMapRepository repository) {
        this.templatePath = templatePath;
        this.templatesDir = templatesDir;
        this.repository = repository;
        try {
            this.defaultTemplate = ServerTemplate.load(templatePath);
        } catch (Exception e) {
            LOGGER.error("Template inválido na inicialização, usando o padrão embutido até o próximo /sync-server: {}", e.getMessage());
            this.defaultTemplate = ServerTemplate.load("");
        }
        logProblems(defaultTemplate);
    }

    /**
     * Template padrão (usado quando não há contexto de guilda, ex.: status do bot).
     */
    public ServerTemplate get() {
        return defaultTemplate;
    }

    public ServerTemplate get(Guild guild) {
        if (guild == null) return defaultTemplate;
        ServerTemplate cached = guildTemplates.get(guild.getId());
        if (cached != null) return cached;
        File custom = guildTemplateFile(guild);
        if (custom.isFile()) {
            try {
                ServerTemplate loaded = ServerTemplate.load(custom.getPath());
                guildTemplates.put(guild.getId(), loaded);
                return loaded;
            } catch (Exception e) {
                LOGGER.error("Template de {} inválido ({}); usando o padrão", guild.getName(), e.getMessage());
            }
        }
        return defaultTemplate;
    }

    /**
     * Relê do disco o template da guilda (chamado por /setup-server, /sync-server e /rebuild-server).
     * Lança exceção se o JSON estiver quebrado.
     */
    public ServerTemplate reload(Guild guild) {
        File custom = guildTemplateFile(guild);
        ServerTemplate loaded;
        if (custom.isFile()) {
            loaded = ServerTemplate.load(custom.getPath());
            guildTemplates.put(guild.getId(), loaded);
            LOGGER.info("Usando template próprio de {}: {}", guild.getName(), custom.getPath());
        } else {
            loaded = ServerTemplate.load(templatePath);
            defaultTemplate = loaded;
            guildTemplates.remove(guild.getId());
        }
        logProblems(loaded);
        channelCache.remove(guild.getId());
        return loaded;
    }

    public String templateSource(Guild guild) {
        File custom = guildTemplateFile(guild);
        return custom.isFile() ? custom.getPath() : templatePath;
    }

    private File guildTemplateFile(Guild guild) {
        return new File(templatesDir, guild.getId() + ".json");
    }

    public void invalidate(Guild guild) {
        channelCache.remove(guild.getId());
    }

    private void logProblems(ServerTemplate template) {
        var problems = template.validate();
        if (!problems.isEmpty()) {
            LOGGER.warn("Template '{}' possui {} problema(s):", template.getTemplateName(), problems.size());
            problems.forEach(p -> LOGGER.warn("  - {}", p));
        }
    }

    // ---------------------------------------------------------------- recursos mapeados

    public Optional<ChannelTemplate> channelTemplateFor(Guild guild, String channelId) {
        Map<String, ChannelTemplate> byId = channelCache.computeIfAbsent(guild.getId(), id -> {
            Map<String, ChannelTemplate> map = new HashMap<>();
            get(guild).allChannels().forEach(tpl ->
                    repository.getDiscordId(id, "CHANNEL", tpl.getKey()).ifPresent(discordId -> map.put(discordId, tpl)));
            return map;
        });
        return Optional.ofNullable(byId.get(channelId));
    }

    public Optional<Role> role(Guild guild, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return repository.getDiscordId(guild.getId(), "ROLE", key).map(guild::getRoleById);
    }

    public Optional<GuildChannel> channel(Guild guild, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return repository.getDiscordId(guild.getId(), "CHANNEL", key).map(guild::getGuildChannelById);
    }

    public Optional<GuildMessageChannel> messageChannel(Guild guild, String key) {
        return channel(guild, key)
                .filter(GuildMessageChannel.class::isInstance)
                .map(GuildMessageChannel.class::cast);
    }

    public Optional<Category> category(Guild guild, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return repository.getDiscordId(guild.getId(), "CATEGORY", key).map(guild::getCategoryById);
    }

    // ---------------------------------------------------------------- textos e identidade visual

    /**
     * Substitui {server}, {brand}, {channel:KEY} e {role:KEY} pelos valores reais da guilda.
     * Placeholders de usuário ({user}, {count}...) são tratados por quem chama, via {@code extra}.
     */
    public String render(Guild guild, String text, Map<String, String> extra) {
        if (text == null) return null;
        ServerTemplate template = get(guild);
        String result = text
                .replace("{server}", guild.getName())
                .replace("{brand}", template.getBranding().getName())
                .replace("{currency}", template.getEconomy().getCurrencyName())
                .replace("{coin}", template.getEconomy().getCurrencyEmoji());
        if (extra != null) {
            for (var entry : extra.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        Matcher matcher = KEY_PLACEHOLDER.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(2);
            String replacement = matcher.group(1).equals("channel")
                    ? channel(guild, key).map(GuildChannel::getAsMention).orElse("`#" + key.toLowerCase() + "`")
                    : role(guild, key).map(Role::getAsMention).orElse("`@" + key.toLowerCase() + "`");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String render(Guild guild, String text) {
        return render(guild, text, null);
    }

    /**
     * Mensagem do bot pela chave: usa "messages" do template ou o padrão de messages.properties.
     * Pares em {@code vars} viram placeholders: msg(guild, "economy.balance", "user", "@Fulano", "balance", "10").
     */
    public String msg(Guild guild, String key, Object... vars) {
        String text = get(guild).getMessages().getOrDefault(key, defaultMessages.getProperty(key, key));
        Map<String, String> extra = new HashMap<>();
        for (int i = 0; i + 1 < vars.length; i += 2) {
            extra.put(String.valueOf(vars[i]), String.valueOf(vars[i + 1]));
        }
        return render(guild, text, extra);
    }

    public Color brandColor(Guild guild) {
        return parseColor(get(guild).getBranding().getColorHex(), new Color(0xE3B55B));
    }

    /**
     * EmbedBuilder já com cor, rodapé e timestamp da identidade visual do servidor.
     */
    public EmbedBuilder brandedEmbed(Guild guild) {
        BrandingConfig branding = get(guild).getBranding();
        String icon = branding.getIconUrl() != null && !branding.getIconUrl().isBlank()
                ? branding.getIconUrl()
                : guild.getIconUrl();
        return new EmbedBuilder()
                .setColor(brandColor(guild))
                .setFooter(branding.getFooter(), icon)
                .setTimestamp(Instant.now());
    }

    public static Color parseColor(String hex, Color defaultColor) {
        if (hex == null || hex.isBlank()) return defaultColor;
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    private static Properties loadDefaultMessages() {
        Properties props = new Properties();
        try (InputStream is = TemplateService.class.getResourceAsStream("/messages.properties")) {
            if (is != null) props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.warn("messages.properties não carregado: {}", e.getMessage());
        }
        return props;
    }
}
