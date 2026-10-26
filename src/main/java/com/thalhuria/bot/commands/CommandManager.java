package com.thalhuria.bot.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Gerenciador central de comandos Slash do bot.
 * Registra os comandos na API do Discord e despacha as interações recebidas para os respectivos handlers.
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, SlashCommandHandler> commands = new HashMap<>();
    private final Map<String, SlashCommandData> commandData = new LinkedHashMap<>();

    public void registerCommand(SlashCommandHandler handler) {
        for (SlashCommandData data : handler.getAllCommandData()) {
            commands.put(data.getName(), handler);
            commandData.put(data.getName(), data);
            LOGGER.info("Comando registrado internamente: /{}", data.getName());
        }
    }

    /**
     * Publica e atualiza os comandos slash registrados na API do Discord.
     * Caso um testGuildId seja fornecido, registra diretamente na guilda para atualização instantânea.
     */
    public void publishCommands(JDA jda, String testGuildId) {
        List<SlashCommandData> commandDataList = List.copyOf(commandData.values());

        if (testGuildId != null && !testGuildId.isBlank()) {
            Guild testGuild = jda.getGuildById(testGuildId);
            if (testGuild != null) {
                testGuild.updateCommands().addCommands(commandDataList).queue(
                        success -> LOGGER.info("Comandos Slash registrados com sucesso na guilda de testes {} ({})!", testGuild.getName(), testGuildId),
                        error -> LOGGER.error("Falha ao registrar comandos na guilda de testes: {}", error.getMessage())
                );
                // Registrar também globalmente faria cada comando aparecer duas vezes na guilda de testes
                jda.updateCommands().queue();
                return;
            }
            LOGGER.warn("Guilda de testes com ID {} não foi encontrada pelo bot; registrando globalmente.", testGuildId);
        }

        jda.updateCommands().addCommands(commandDataList).queue(
                success -> LOGGER.info("{} comandos Slash registrados globalmente com sucesso!", commandDataList.size()),
                error -> LOGGER.error("Falha ao registrar comandos globalmente: {}", error.getMessage())
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        SlashCommandHandler handler = commands.get(commandName);

        if (handler != null) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                LOGGER.error("Erro inesperado ao executar o comando /{}: {}", commandName, e.getMessage(), e);
                if (event.isAcknowledged()) {
                    event.getHook().sendMessage("❌ Erro interno ao processar o comando: " + e.getMessage()).queue();
                } else {
                    event.reply("❌ Erro interno ao processar o comando: " + e.getMessage()).setEphemeral(true).queue();
                }
            }
        } else {
            LOGGER.warn("Comando não manipulado: /{}", commandName);
            event.reply("Comando não reconhecido ou em manutenção.").setEphemeral(true).queue();
        }
    }
}
