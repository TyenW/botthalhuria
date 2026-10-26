package com.thalhuria.bot.service;

import com.thalhuria.bot.config.AutoModConfig;
import com.thalhuria.bot.config.ModerationConfig;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.config.WarnThreshold;
import com.thalhuria.bot.database.InfractionRepository;
import com.thalhuria.bot.database.InfractionRepository.Infraction;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Ferramentas de moderação: advertências com escalonamento automático, castigo (timeout nativo),
 * expulsão, banimento, limpeza de mensagens, histórico e regras do AutoMod nativo do Discord.
 */
public class ModerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModerationService.class);
    /** Prefixo das regras de AutoMod criadas pelo bot (as demais regras do servidor nunca são tocadas). */
    public static final String AUTOMOD_PREFIX = "[Bot] ";
    private static final String INVITE_PATTERN = "(discord\\.gg|discord(app)?\\.com/invite)/[a-zA-Z0-9-]+";

    private final InfractionRepository infractions;
    private final TemplateService templateService;
    private final ModLogService modLog;

    public ModerationService(InfractionRepository infractions, TemplateService templateService, ModLogService modLog) {
        this.infractions = infractions;
        this.templateService = templateService;
        this.modLog = modLog;
    }

    // ---------------------------------------------------------------- ações

    /**
     * Registra uma advertência e aplica a punição automática se algum limite for atingido. Bloqueante.
     */
    public String warn(Member moderator, Member target, String reason) {
        Guild guild = target.getGuild();
        Optional<String> denied = checkHierarchy(moderator, target);
        if (denied.isPresent()) return denied.get();

        long id = infractions.add(guild.getId(), target.getId(), moderator.getId(), "WARN", reason);
        ModerationConfig config = templateService.get(guild).getModeration();
        int active = infractions.countActiveWarnings(guild.getId(), target.getId(), config.getWarningExpiryDays());

        notifyUser(target.getUser(), guild, "⚠️ Advertência", reason, active);
        modLog.send(guild, action("⚠️ Advertência #" + id, new Color(0xF59E0B), target.getUser(), moderator, reason)
                .addField("Advertências ativas", String.valueOf(active), true).build());

        String auto = applyThreshold(guild, target, active);
        return templateService.msg(guild, "mod.warn.ok", "user", target.getAsMention(), "count", active)
                + (auto != null ? "\n" + auto : "");
    }

    public String timeout(Member moderator, Member target, int minutes, String reason) {
        Guild guild = target.getGuild();
        Optional<String> denied = checkHierarchy(moderator, target);
        if (denied.isPresent()) return denied.get();
        if (minutes < 1 || minutes > 40320) return templateService.msg(guild, "mod.timeout.range");

        target.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).complete();
        long id = infractions.add(guild.getId(), target.getId(), moderator.getId(), "TIMEOUT", minutes + " min • " + reason);
        notifyUser(target.getUser(), guild, "🔇 Castigo de " + minutes + " minuto(s)", reason, -1);
        modLog.send(guild, action("🔇 Castigo #" + id, new Color(0xF97316), target.getUser(), moderator, reason)
                .addField("Duração", minutes + " min", true).build());
        return templateService.msg(guild, "mod.timeout.ok", "user", target.getAsMention(), "minutes", minutes);
    }

    public String untimeout(Member moderator, Member target) {
        Guild guild = target.getGuild();
        target.removeTimeout().reason("Removido por " + moderator.getUser().getName()).complete();
        modLog.send(guild, action("🔊 Castigo removido", new Color(0x22C55E), target.getUser(), moderator, "—").build());
        return templateService.msg(guild, "mod.untimeout.ok", "user", target.getAsMention());
    }

    public String kick(Member moderator, Member target, String reason) {
        Guild guild = target.getGuild();
        Optional<String> denied = checkHierarchy(moderator, target);
        if (denied.isPresent()) return denied.get();

        notifyUser(target.getUser(), guild, "👢 Você foi expulso(a)", reason, -1);
        target.kick().reason(reason).complete();
        long id = infractions.add(guild.getId(), target.getId(), moderator.getId(), "KICK", reason);
        modLog.send(guild, action("👢 Expulsão #" + id, new Color(0xEF4444), target.getUser(), moderator, reason).build());
        return templateService.msg(guild, "mod.kick.ok", "user", target.getUser().getName());
    }

    public String ban(Member moderator, User target, String reason, int deleteDays) {
        Guild guild = moderator.getGuild();
        Member targetMember = guild.getMember(target);
        if (targetMember != null) {
            Optional<String> denied = checkHierarchy(moderator, targetMember);
            if (denied.isPresent()) return denied.get();
            notifyUser(target, guild, "🔨 Você foi banido(a)", reason, -1);
        }
        guild.ban(target, Math.max(0, Math.min(7, deleteDays)), TimeUnit.DAYS).reason(reason).complete();
        long id = infractions.add(guild.getId(), target.getId(), moderator.getId(), "BAN", reason);
        modLog.send(guild, action("🔨 Banimento #" + id, new Color(0x991B1B), target, moderator, reason).build());
        return templateService.msg(guild, "mod.ban.ok", "user", target.getName());
    }

    /**
     * Apaga as últimas mensagens do canal (opcionalmente só de um usuário). Mensagens com mais de 14 dias
     * não podem ser apagadas em massa pelo Discord e são ignoradas.
     */
    public String clear(Member moderator, GuildMessageChannel channel, int amount, User onlyFrom) {
        Guild guild = moderator.getGuild();
        int limit = Math.max(1, Math.min(100, amount));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14).plusMinutes(5);

        List<Message> history = channel.getIterableHistory().takeAsync(onlyFrom == null ? limit : 500).join();
        List<Message> toDelete = history.stream()
                .filter(m -> onlyFrom == null || m.getAuthor().equals(onlyFrom))
                .filter(m -> m.getTimeCreated().isAfter(cutoff))
                .limit(limit)
                .toList();
        if (toDelete.isEmpty()) return templateService.msg(guild, "mod.clear.none");

        channel.purgeMessages(toDelete);
        modLog.send(guild, new EmbedBuilder()
                .setTitle("🧹 Mensagens apagadas")
                .setColor(new Color(0x64748B))
                .addField("Canal", channel.getAsMention(), true)
                .addField("Quantidade", String.valueOf(toDelete.size()), true)
                .addField("Moderador", moderator.getAsMention(), true)
                .addField("Filtro", onlyFrom != null ? onlyFrom.getAsMention() : "—", true)
                .setTimestamp(OffsetDateTime.now()).build());
        return templateService.msg(guild, "mod.clear.ok", "count", toDelete.size());
    }

    public MessageEmbed history(Guild guild, User target) {
        List<Infraction> list = infractions.list(guild.getId(), target.getId());
        int expiry = templateService.get(guild).getModeration().getWarningExpiryDays();
        int activeWarnings = infractions.countActiveWarnings(guild.getId(), target.getId(), expiry);

        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle("📋 Histórico de " + target.getName())
                .setThumbnail(target.getEffectiveAvatarUrl())
                .setDescription("**" + list.size() + "** registro(s) · **" + activeWarnings + "** advertência(s) ativa(s)");
        list.stream().limit(15).forEach(inf -> embed.addField(
                (inf.active() ? "" : "~~") + "#" + inf.id() + " · " + typeLabel(inf.type()) + (inf.active() ? "" : "~~"),
                "<t:" + inf.createdAt() + ":d> por <@" + inf.moderatorId() + ">\n" + truncate(String.valueOf(inf.reason()), 200),
                false));
        if (list.size() > 15) embed.setFooter("Mostrando os 15 mais recentes de " + list.size());
        return embed.build();
    }

    public String pardon(Member moderator, long infractionId) {
        Guild guild = moderator.getGuild();
        if (!infractions.deactivate(guild.getId(), infractionId)) return templateService.msg(guild, "mod.pardon.unknown");
        modLog.send(guild, new EmbedBuilder()
                .setTitle("🕊️ Infração #" + infractionId + " perdoada")
                .setColor(new Color(0x22C55E))
                .addField("Moderador", moderator.getAsMention(), true)
                .setTimestamp(OffsetDateTime.now()).build());
        return templateService.msg(guild, "mod.pardon.ok", "id", infractionId);
    }

    // ---------------------------------------------------------------- escalonamento

    private String applyThreshold(Guild guild, Member target, int activeWarnings) {
        ModerationConfig config = templateService.get(guild).getModeration();
        Optional<WarnThreshold> threshold = config.getWarnThresholds().stream()
                .filter(t -> t.getWarnings() == activeWarnings)
                .max(Comparator.comparingInt(WarnThreshold::getWarnings));
        if (threshold.isEmpty()) return null;

        Member self = guild.getSelfMember();
        String reason = "Punição automática: " + activeWarnings + " advertências";
        try {
            switch (threshold.get().getAction().toUpperCase()) {
                case "TIMEOUT" -> {
                    target.timeoutFor(Duration.ofMinutes(threshold.get().getDurationMinutes())).reason(reason).complete();
                    infractions.add(guild.getId(), target.getId(), self.getId(), "TIMEOUT", threshold.get().getDurationMinutes() + " min • " + reason);
                    modLog.send(guild, action("🤖 Castigo automático", new Color(0xF97316), target.getUser(), self, reason).build());
                    return templateService.msg(guild, "mod.auto.timeout", "minutes", threshold.get().getDurationMinutes());
                }
                case "KICK" -> {
                    target.kick().reason(reason).complete();
                    infractions.add(guild.getId(), target.getId(), self.getId(), "KICK", reason);
                    modLog.send(guild, action("🤖 Expulsão automática", new Color(0xEF4444), target.getUser(), self, reason).build());
                    return templateService.msg(guild, "mod.auto.kick");
                }
                case "BAN" -> {
                    guild.ban(target, 0, TimeUnit.DAYS).reason(reason).complete();
                    infractions.add(guild.getId(), target.getId(), self.getId(), "BAN", reason);
                    modLog.send(guild, action("🤖 Banimento automático", new Color(0x991B1B), target.getUser(), self, reason).build());
                    return templateService.msg(guild, "mod.auto.ban");
                }
                default -> {
                    return null;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Falha na punição automática de {}: {}", target.getEffectiveName(), e.getMessage());
            return "⚠️ Punição automática falhou: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- AutoMod nativo

    /**
     * Recria as regras de AutoMod do bot a partir do template. Regras criadas manualmente pela staff não são alteradas.
     *
     * @return quantidade de regras ativas criadas
     */
    public int syncAutoMod(Guild guild, ServerTemplate template) {
        AutoModConfig config = template.getModeration().getAutomod();
        List<AutoModRule> existing = guild.retrieveAutoModRules().complete();
        for (AutoModRule rule : existing) {
            if (rule.getName().startsWith(AUTOMOD_PREFIX)) {
                rule.getGuild().deleteAutoModRuleById(rule.getId()).complete();
            }
        }
        if (!config.isEnabled()) return 0;

        List<Role> exempt = new ArrayList<>();
        for (String key : config.getExemptRoleKeys()) templateService.role(guild, key).ifPresent(exempt::add);
        Optional<GuildMessageChannel> alert = templateService.messageChannel(guild, template.getModeration().getModLogChannelKey());

        int created = 0;
        if (!config.getBlockedWords().isEmpty()) {
            created += create(guild, "Palavras proibidas", TriggerConfig.keywordFilter(config.getBlockedWords()), config, exempt, alert, true);
        }
        if (config.isBlockInvites()) {
            created += create(guild, "Convites de outros servidores", TriggerConfig.patternFilter(INVITE_PATTERN), config, exempt, alert, true);
        }
        if (config.getMentionLimit() > 0) {
            created += create(guild, "Menções em massa",
                    TriggerConfig.mentionSpam(config.getMentionLimit()).setMentionRaidProtectionEnabled(true), config, exempt, alert, true);
        }
        if (config.isAntiSpam()) {
            // O Discord não permite castigo automático em regras de spam
            created += create(guild, "Spam", TriggerConfig.antiSpam(), config, exempt, alert, false);
        }
        LOGGER.info("AutoMod sincronizado em {}: {} regra(s)", guild.getName(), created);
        return created;
    }

    private int create(Guild guild, String name, TriggerConfig trigger, AutoModConfig config, List<Role> exempt,
                       Optional<GuildMessageChannel> alert, boolean allowTimeout) {
        List<AutoModResponse> responses = new ArrayList<>();
        responses.add(AutoModResponse.blockMessage(truncate(config.getBlockMessage(), AutoModResponse.MAX_CUSTOM_MESSAGE_LENGTH)));
        alert.ifPresent(ch -> responses.add(AutoModResponse.sendAlert(ch)));
        if (allowTimeout && config.getTimeoutMinutes() > 0) {
            responses.add(AutoModResponse.timeoutMember(Duration.ofMinutes(config.getTimeoutMinutes())));
        }
        try {
            guild.createAutoModRule(AutoModRuleData.onMessage(AUTOMOD_PREFIX + name, trigger)
                    .setResponses(responses)
                    .setExemptRoles(exempt)
                    .setEnabled(true)).complete();
            return 1;
        } catch (Exception e) {
            LOGGER.warn("Não foi possível criar a regra de AutoMod '{}': {}", name, e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------- utilitários

    private Optional<String> checkHierarchy(Member moderator, Member target) {
        Guild guild = target.getGuild();
        if (target.equals(moderator)) return Optional.of(templateService.msg(guild, "mod.self"));
        if (target.isOwner() || !guild.getSelfMember().canInteract(target)) return Optional.of(templateService.msg(guild, "mod.cannot.bot"));
        if (!moderator.isOwner() && !moderator.canInteract(target)) return Optional.of(templateService.msg(guild, "mod.cannot.you"));
        return Optional.empty();
    }

    private void notifyUser(User user, Guild guild, String title, String reason, int warnings) {
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle(title)
                .setDescription("Servidor: **" + guild.getName() + "**")
                .addField("Motivo", truncate(reason, 1000), false);
        if (warnings > 0) embed.addField("Advertências ativas", String.valueOf(warnings), true);
        user.openPrivateChannel().flatMap(dm -> dm.sendMessageEmbeds(embed.build())).queue(null, err -> { });
    }

    private EmbedBuilder action(String title, Color color, User target, Member moderator, String reason) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setThumbnail(target.getEffectiveAvatarUrl())
                .addField("Membro", target.getAsMention() + " (`" + target.getId() + "`)", true)
                .addField("Moderador", moderator.getAsMention(), true)
                .addField("Motivo", truncate(reason == null ? "—" : reason, 1000), false)
                .setTimestamp(OffsetDateTime.now());
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "WARN" -> "⚠️ Advertência";
            case "TIMEOUT" -> "🔇 Castigo";
            case "KICK" -> "👢 Expulsão";
            case "BAN" -> "🔨 Banimento";
            default -> type;
        };
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
