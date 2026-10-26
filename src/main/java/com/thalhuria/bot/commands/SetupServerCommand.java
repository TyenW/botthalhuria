package com.thalhuria.bot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comando /setup-server: apaga TODOS os canais e cargos do servidor e cria tudo do zero a partir do template.
 * Pede confirmação por botão; a execução é a mesma do /rebuild-server (modo setup).
 * Para aplicar mudanças sem apagar nada, use /sync-server.
 */
public class SetupServerCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupServerCommand.class);
    private final RebuildServerCommand rebuildCommand;

    public SetupServerCommand(RebuildServerCommand rebuildCommand) {
        this.rebuildCommand = rebuildCommand;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("setup-server", "Apaga todos os canais e cargos e cria o servidor do zero pelo template")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        LOGGER.info("Executando /setup-server por {}", event.getUser().getName());
        rebuildCommand.prompt(event, RebuildServerCommand.MODE_SETUP);
    }
}
