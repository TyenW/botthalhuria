package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.CommandsConfig;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
    private final TemplateService templateService;

    public CommandManager(TemplateService templateService) {
        this.templateService = templateService;
    }

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
            String allowed = blockedOutsideCommandChannels(event);
            if (allowed != null) {
                event.reply(templateService.msg(event.getGuild(), "commands.wrong.channel", "channels", allowed))
                        .setEphemeral(true).queue();
                return;
            }
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

    /**
     * @return menção dos canais permitidos se o comando foi usado fora deles; null se pode executar aqui
     */
    private String blockedOutsideCommandChannels(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || member.hasPermission(Permission.ADMINISTRATOR)) return null;

        CommandsConfig config = templateService.get(guild).getCommands();
        if (!config.isRestrictToChannels()) return null;
        boolean bypass = config.getBypassRoleKeys().stream()
                .map(key -> templateService.role(guild, key))
                .anyMatch(role -> role.isPresent() && member.getRoles().contains(role.get()));
        if (bypass) return null;

        List<GuildChannel> allowed = config.getAllowedChannelKeys().stream()
                .map(key -> templateService.channel(guild, key))
                .flatMap(Optional::stream)
                .toList();
        // Servidor ainda sem os canais do template: não trava ninguém
        if (allowed.isEmpty()) return null;

        long channelId = event.getChannel().getIdLong();
        long parentId = event.getChannelType().isThread()
                ? event.getChannel().asThreadChannel().getParentChannel().getIdLong()
                : channelId;
        if (allowed.stream().anyMatch(ch -> ch.getIdLong() == channelId || ch.getIdLong() == parentId)) return null;

        // Só lista os canais que o membro enxerga (canais da staff ficam de fora da resposta)
        return allowed.stream()
                .filter(ch -> ch.getType() != ChannelType.VOICE && member.hasAccess(ch))
                .map(GuildChannel::getAsMention)
                .reduce((a, b) -> a + ", " + b)
                .orElse(allowed.get(0).getAsMention());
    }
}
