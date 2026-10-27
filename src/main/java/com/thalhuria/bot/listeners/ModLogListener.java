package com.thalhuria.bot.listeners;

import com.thalhuria.bot.config.ModerationConfig;
import com.thalhuria.bot.config.Palette;
import com.thalhuria.bot.service.ModLogService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Logs de moderação: mensagens apagadas e editadas, banimentos, desbanimentos, castigos e movimentação em calls.
 *
 * O Discord não envia o conteúdo de uma mensagem apagada, então o bot guarda as últimas mensagens em memória.
 * O conteúdo só é conhecido com MESSAGE_CONTENT_INTENT=true (intent privilegiada); sem ela, o log mostra autor e canal.
 */
public class ModLogListener extends ListenerAdapter {

    private static final int CACHE_SIZE = 10_000;
    private static final Color RED = Palette.RUBY;
    private static final Color AMBER = Palette.GOLD;
    private static final Color GREEN = new Color(0x22C55E);
    private static final Color SLATE = new Color(0x64748B);

    private record CachedMessage(String authorId, String authorName, String channelId, String content, int attachments) {}

    private final Map<String, CachedMessage> cache = Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    private final ModLogService modLog;
    private final TemplateService templateService;

    public ModLogListener(ModLogService modLog, TemplateService templateService) {
        this.modLog = modLog;
        this.templateService = templateService;
    }

    private ModerationConfig config(Guild guild) {
        return templateService.get(guild).getModeration();
    }

    // ---------------------------------------------------------------- mensagens

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        Message m = event.getMessage();
        cache.put(m.getId(), new CachedMessage(m.getAuthor().getId(), m.getAuthor().getName(), m.getChannel().getId(),
                m.getContentRaw(), m.getAttachments().size()));
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (!event.isFromGuild() || !config(event.getGuild()).isLogMessageDeletes()) return;
        CachedMessage cached = cache.remove(event.getMessageId());
        if (cached == null) return; // mensagem antiga, de bot ou anterior à inicialização

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🗑️ Mensagem apagada")
                .setColor(RED)
                .addField("Autor", "<@" + cached.authorId() + "> (" + cached.authorName() + ")", true)
                .addField("Canal", "<#" + cached.channelId() + ">", true)
                .addField("Conteúdo", content(cached.content(), cached.attachments()), false)
                .setFooter("ID da mensagem: " + event.getMessageId())
                .setTimestamp(Instant.now());
        modLog.send(event.getGuild(), embed.build());
    }

    @Override
    public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
        if (!config(event.getGuild()).isLogMessageDeletes()) return;
        event.getMessageIds().forEach(cache::remove);
        modLog.send(event.getGuild(), new EmbedBuilder()
                .setTitle("🗑️ Exclusão em massa")
                .setColor(RED)
                .addField("Canal", event.getChannel().getAsMention(), true)
                .addField("Mensagens", String.valueOf(event.getMessageIds().size()), true)
                .setTimestamp(Instant.now()).build());
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        Message m = event.getMessage();
        CachedMessage before = cache.put(m.getId(), new CachedMessage(m.getAuthor().getId(), m.getAuthor().getName(),
                m.getChannel().getId(), m.getContentRaw(), m.getAttachments().size()));
        if (!config(event.getGuild()).isLogMessageEdits() || before == null) return;
        if (before.content().equals(m.getContentRaw()) || m.getContentRaw().isBlank()) return; // só embed/anexo mudou

        modLog.send(event.getGuild(), new EmbedBuilder()
                .setTitle("✏️ Mensagem editada")
                .setColor(AMBER)
                .setDescription("[Ir para a mensagem](" + m.getJumpUrl() + ")")
                .addField("Autor", m.getAuthor().getAsMention(), true)
                .addField("Canal", m.getChannel().getAsMention(), true)
                .addField("Antes", content(before.content(), 0), false)
                .addField("Depois", content(m.getContentRaw(), 0), false)
                .setTimestamp(Instant.now()).build());
    }

    // ---------------------------------------------------------------- punições

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        withAuditEntry(event.getGuild(), ActionType.BAN, event.getUser(), entry ->
                modLog.send(event.getGuild(), new EmbedBuilder()
                        .setTitle("🔨 Membro banido")
                        .setColor(Palette.IMPERIAL)
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .addField("Membro", event.getUser().getAsMention() + " (`" + event.getUser().getId() + "`)", true)
                        .addField("Por", entry.map(e -> "<@" + e.getUserId() + ">").orElse("Desconhecido"), true)
                        .addField("Motivo", entry.map(AuditLogEntry::getReason).filter(r -> r != null && !r.isBlank()).orElse("—"), false)
                        .setTimestamp(Instant.now()).build()));
    }

    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
        withAuditEntry(event.getGuild(), ActionType.UNBAN, event.getUser(), entry ->
                modLog.send(event.getGuild(), new EmbedBuilder()
                        .setTitle("🕊️ Banimento removido")
                        .setColor(GREEN)
                        .addField("Membro", event.getUser().getAsMention() + " (`" + event.getUser().getId() + "`)", true)
                        .addField("Por", entry.map(e -> "<@" + e.getUserId() + ">").orElse("Desconhecido"), true)
                        .setTimestamp(Instant.now()).build()));
    }

    @Override
    public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
        boolean started = event.getNewTimeOutEnd() != null;
        modLog.send(event.getGuild(), new EmbedBuilder()
                .setTitle(started ? "🔇 Membro de castigo" : "🔊 Castigo encerrado")
                .setColor(started ? AMBER : GREEN)
                .addField("Membro", event.getMember().getAsMention(), true)
                .addField(started ? "Até" : "Status", started
                        ? "<t:" + event.getNewTimeOutEnd().toEpochSecond() + ":f>"
                        : "Pode falar novamente", true)
                .setTimestamp(Instant.now()).build());
    }

    // ---------------------------------------------------------------- voz

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot() || !config(event.getGuild()).isLogVoice()) return;
        String user = event.getMember().getAsMention();
        String text;
        Color color;
        if (event.getChannelLeft() == null) {
            text = "📥 " + user + " entrou em " + event.getChannelJoined().getAsMention();
            color = GREEN;
        } else if (event.getChannelJoined() == null) {
            text = "📤 " + user + " saiu de " + event.getChannelLeft().getAsMention();
            color = SLATE;
        } else {
            text = "🔀 " + user + " foi de " + event.getChannelLeft().getAsMention() + " para " + event.getChannelJoined().getAsMention();
            color = AMBER;
        }
        modLog.send(event.getGuild(), new EmbedBuilder().setDescription(text).setColor(color).setTimestamp(Instant.now()).build());
    }

    // ---------------------------------------------------------------- utilitários

    /**
     * Busca no registro de auditoria quem executou a ação (o evento do gateway não informa).
     */
    private void withAuditEntry(Guild guild, ActionType type, User target, java.util.function.Consumer<Optional<AuditLogEntry>> callback) {
        guild.retrieveAuditLogs().type(type).limit(5).queueAfter(1, TimeUnit.SECONDS,
                entries -> callback.accept(entries.stream().filter(e -> e.getTargetIdLong() == target.getIdLong()).findFirst()),
                err -> callback.accept(Optional.empty()));
    }

    private static String content(String text, int attachments) {
        String value = text == null || text.isBlank() ? "*(sem texto — ative MESSAGE_CONTENT_INTENT para ver o conteúdo)*" : text;
        if (attachments > 0) value += "\n📎 " + attachments + " anexo(s)";
        return value.length() > 1024 ? value.substring(0, 1021) + "…" : value;
    }
}
