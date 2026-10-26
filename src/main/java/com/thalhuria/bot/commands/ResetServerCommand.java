package com.thalhuria.bot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;

/**
 * Comando /reset-server: Apresenta tela de confirmação com ActionRows e Botões
 * para limpeza segura e redefinição de ambiente de testes.
 */
public class ResetServerCommand implements SlashCommandHandler {

    public static final String BTN_CONFIRM_PREFIX = "btn_reset_confirm_";
    public static final String BTN_CANCEL_PREFIX = "btn_reset_cancel_";

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("reset-server", "Limpa e redefine todos os canais, categorias e cargos gerenciados pelo bot")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando só pode ser executado em servidores!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();

        EmbedBuilder warningEmbed = new EmbedBuilder()
                .setTitle("⚠️ Confirmação de Redefinição do Servidor")
                .setColor(new Color(0xE74C3C))
                .setDescription("Você solicitou a **redefinição completa (reset)** dos recursos gerenciados pelo bot neste servidor.\n\n" +
                        "**O que será excluído:**\n" +
                        "• Todos os canais e categorias criados pelo template\n" +
                        "• Tudo que estiver dentro dessas categorias (salas privadas, tickets, salas de voz temporárias)\n" +
                        "• Todos os cargos gerenciados pelo bot (Admin, Moderador, Membro, etc.)\n" +
                        "• Mapeamentos de IDs salvos no banco de dados SQLite\n\n" +
                        "**Esta ação é irreversível.** Tem certeza de que deseja continuar?")
                .setFooter("Ambiente de Testes • Bot Thaluria", event.getUser().getEffectiveAvatarUrl())
                .setTimestamp(Instant.now());

        event.replyEmbeds(warningEmbed.build())
                .addActionRow(
                        Button.danger(BTN_CONFIRM_PREFIX + userId, "Sim, Resetar Tudo"),
                        Button.secondary(BTN_CANCEL_PREFIX + userId, "Cancelar Ação")
                )
                .queue();
    }
}
