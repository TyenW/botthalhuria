package com.thalhuria.bot.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Envia registros para o canal de logs de moderação (moderation.modLogChannelKey).
 * Sem esse canal no template, usa o canal de logs do sistema.
 */
public class ModLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModLogService.class);

    private final TemplateService templateService;
    private final AuditLogService auditLogService;

    public ModLogService(TemplateService templateService, AuditLogService auditLogService) {
        this.templateService = templateService;
        this.auditLogService = auditLogService;
    }

    public Optional<GuildMessageChannel> channel(Guild guild) {
        Optional<GuildMessageChannel> mod = templateService.messageChannel(guild,
                templateService.get(guild).getModeration().getModLogChannelKey());
        if (mod.isPresent() && mod.get().canTalk()) return mod;
        return auditLogService.findLogChannel(guild).map(GuildMessageChannel.class::cast);
    }

    public void send(Guild guild, MessageEmbed embed) {
        channel(guild).ifPresent(ch -> ch.sendMessageEmbeds(embed).queue(null,
                err -> LOGGER.warn("Falha ao enviar log de moderação: {}", err.getMessage())));
    }

    public void send(Guild guild, MessageEmbed embed, FileUpload file) {
        channel(guild).ifPresent(ch -> ch.sendMessageEmbeds(embed).addFiles(file).queue(null,
                err -> LOGGER.warn("Falha ao enviar log de moderação com anexo: {}", err.getMessage())));
    }
}
