package com.thalhuria.bot.listeners;

import com.thalhuria.bot.commands.ResetServerCommand;
import com.thalhuria.bot.service.AuditLogService;
import com.thalhuria.bot.service.ServerProvisioningService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Collections;

/**
 * Listener responsável por interceptar interações com botões do Discord,
 * especificamente a confirmação e cancelamento do comando /reset-server.
 */
public class ButtonInteractionListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ButtonInteractionListener.class);
    private final ServerProvisioningService provisioningService;
    private final AuditLogService auditLogService;

    public ButtonInteractionListener(ServerProvisioningService provisioningService, AuditLogService auditLogService) {
        this.provisioningService = provisioningService;
        this.auditLogService = auditLogService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        // Os demais botões (painéis de verificação, tickets...) são tratados pelo PanelInteractionListener
        if (!buttonId.startsWith(ResetServerCommand.BTN_CONFIRM_PREFIX) && !buttonId.startsWith(ResetServerCommand.BTN_CANCEL_PREFIX)) {
            return;
        }
        Guild guild = event.getGuild();

        if (guild == null) {
            event.reply("Esta ação só pode ser realizada dentro de um servidor.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ Apenas administradores do servidor podem confirmar esta ação.").setEphemeral(true).queue();
            return;
        }

        if (buttonId.startsWith(ResetServerCommand.BTN_CONFIRM_PREFIX)) {
            String authorId = buttonId.substring(ResetServerCommand.BTN_CONFIRM_PREFIX.length());
            if (!member.getId().equals(authorId)) {
                event.reply("⛔ Apenas o administrador que executou o comando pode confirmar.").setEphemeral(true).queue();
                return;
            }

            // Desativar botões e notificar início do processo
            EmbedBuilder inProgress = new EmbedBuilder()
                    .setTitle("⏳ Reset em Andamento...")
                    .setColor(new Color(0xE67E22))
                    .setDescription("Excluindo canais, categorias e cargos gerenciados pelo bot e limpando os mapeamentos de IDs.\nPor favor, aguarde alguns instantes...")
                    .setTimestamp(Instant.now());

            event.editMessageEmbeds(inProgress.build())
                    .setComponents(Collections.emptyList())
                    .queue();

            LOGGER.warn("Confirmado /reset-server na Guild {} por {}", guild.getName(), member.getUser().getName());

            // Registrar log antes da exclusão de canais
            auditLogService.logServerReset(guild, member);

            provisioningService.resetServer(guild)
                    .thenRun(() -> {
                        EmbedBuilder successEmbed = new EmbedBuilder()
                                .setTitle("🧹 Servidor Resetado com Sucesso!")
                                .setColor(new Color(0x2ECC71))
                                .setDescription("Todos os recursos gerenciados pelo bot foram removidos e o banco de dados foi limpo.\n\n" +
                                        "👉 Agora você pode executar `/setup-server` para reconstruir o servidor do zero em segundos!")
                                .setFooter("Ambiente de Testes • Bot Thaluria", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                                .setTimestamp(Instant.now());

                        event.getHook().sendMessageEmbeds(successEmbed.build()).queue(
                                success -> LOGGER.info("Mensagem de conclusão de reset enviada"),
                                error -> LOGGER.warn("Não foi possível enviar mensagem de conclusão de reset (o canal onde o comando foi rodado pode ter sido deletado)")
                        );
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Erro durante reset do servidor", ex);
                        event.getHook().sendMessage("❌ Erro ao resetar servidor: " + ex.getMessage()).queue();
                        return null;
                    });

        } else if (buttonId.startsWith(ResetServerCommand.BTN_CANCEL_PREFIX)) {
            String authorId = buttonId.substring(ResetServerCommand.BTN_CANCEL_PREFIX.length());
            if (!member.getId().equals(authorId)) {
                event.reply("⛔ Apenas o administrador que executou o comando pode cancelar.").setEphemeral(true).queue();
                return;
            }

            EmbedBuilder cancelEmbed = new EmbedBuilder()
                    .setTitle("❌ Reset Cancelado")
                    .setColor(new Color(0x95A5A6))
                    .setDescription("A operação de redefinição do servidor foi cancelada com sucesso. Nenhum canal ou cargo foi alterado.")
                    .setTimestamp(Instant.now());

            event.editMessageEmbeds(cancelEmbed.build())
                    .setComponents(Collections.emptyList())
                    .queue();
        }
    }
}
