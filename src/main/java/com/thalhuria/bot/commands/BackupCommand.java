package com.thalhuria.bot.commands;

import com.thalhuria.bot.service.BackupService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * /backup: exporta o servidor atual como template JSON (anexo na resposta + cópia em backups/).
 */
public class BackupCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupCommand.class);
    private final BackupService backupService;
    private final TemplateService templateService;

    public BackupCommand(BackupService backupService, TemplateService templateService) {
        this.backupService = backupService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("backup", "Exporta cargos, canais e permissões atuais como um template JSON")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        event.deferReply(true).queue();
        CompletableFuture.supplyAsync(() -> {
                    try {
                        return backupService.export(guild);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenAccept(backup -> event.getHook()
                        .sendMessage(templateService.msg(guild, "backup.ok",
                                "roles", backup.roles(), "categories", backup.categories(), "channels", backup.channels(),
                                "path", backup.file().getPath().replace('\\', '/'), "id", guild.getId()))
                        .addFiles(FileUpload.fromData(backup.json(), backup.file().getName()))
                        .queue())
                .exceptionally(ex -> {
                    LOGGER.error("Falha no backup", ex);
                    event.getHook().sendMessage("❌ Falha ao gerar o backup: " + ex.getMessage()).queue();
                    return null;
                });
    }
}
