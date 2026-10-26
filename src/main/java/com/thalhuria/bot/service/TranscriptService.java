package com.thalhuria.bot.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gera a transcrição de um canal (tickets) como uma página HTML autocontida, no visual escuro do Discord.
 */
public class TranscriptService {

    private static final int MAX_MESSAGES = 2000;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    /**
     * Lê o histórico do canal (até 2000 mensagens) e devolve o HTML. Bloqueante.
     */
    public byte[] build(TextChannel channel) {
        List<Message> messages = new ArrayList<>(channel.getIterableHistory().takeAsync(MAX_MESSAGES).join());
        Collections.reverse(messages);

        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Transcrição</title>
                <style>
                body{background:#313338;color:#dbdee1;font-family:'gg sans','Segoe UI',Arial,sans-serif;margin:0;padding:24px}
                header{border-bottom:1px solid #3f4147;padding-bottom:12px;margin-bottom:16px}
                h1{font-size:20px;margin:0;color:#f2f3f5}.meta{color:#949ba4;font-size:13px}
                .msg{display:flex;gap:12px;padding:6px 0}.msg img.av{width:40px;height:40px;border-radius:50%}
                .name{font-weight:600;color:#f2f3f5}.bot{background:#5865f2;color:#fff;font-size:10px;border-radius:3px;padding:1px 4px;margin-left:4px}
                .time{color:#949ba4;font-size:12px;margin-left:6px}.content{white-space:pre-wrap;word-break:break-word}
                .embed{border-left:4px solid #5865f2;background:#2b2d31;border-radius:4px;padding:8px 12px;margin-top:4px;max-width:520px}
                .embed b{color:#f2f3f5}a{color:#00a8fc}.att{display:block;margin-top:4px}
                </style></head><body>
                """);
        html.append("<header><h1>#").append(escape(channel.getName())).append("</h1><div class=\"meta\">")
                .append(escape(channel.getGuild().getName())).append(" · ")
                .append(messages.size()).append(" mensagem(ns)")
                .append(channel.getTopic() != null ? " · " + escape(channel.getTopic()) : "")
                .append("</div></header>");

        for (Message m : messages) {
            html.append("<div class=\"msg\"><img class=\"av\" src=\"").append(escape(m.getAuthor().getEffectiveAvatarUrl())).append("\" alt=\"\"><div>")
                    .append("<span class=\"name\">").append(escape(m.getAuthor().getEffectiveName())).append("</span>")
                    .append(m.getAuthor().isBot() ? "<span class=\"bot\">BOT</span>" : "")
                    .append("<span class=\"time\">").append(FORMAT.format(m.getTimeCreated())).append("</span>");
            if (!m.getContentDisplay().isBlank()) {
                html.append("<div class=\"content\">").append(escape(m.getContentDisplay())).append("</div>");
            }
            for (MessageEmbed e : m.getEmbeds()) {
                html.append("<div class=\"embed\">");
                if (e.getTitle() != null) html.append("<b>").append(escape(e.getTitle())).append("</b><br>");
                if (e.getDescription() != null) html.append("<div class=\"content\">").append(escape(e.getDescription())).append("</div>");
                for (MessageEmbed.Field f : e.getFields()) {
                    html.append("<div><b>").append(escape(String.valueOf(f.getName()))).append("</b><br>")
                            .append(escape(String.valueOf(f.getValue()))).append("</div>");
                }
                html.append("</div>");
            }
            for (Message.Attachment a : m.getAttachments()) {
                html.append("<a class=\"att\" href=\"").append(escape(a.getUrl())).append("\">📎 ").append(escape(a.getFileName())).append("</a>");
            }
            html.append("</div></div>");
        }
        html.append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
