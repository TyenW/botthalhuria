package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.service.AuditLogService;
import com.thalhuria.bot.service.ServerProvisioningService;
import com.thalhuria.bot.service.ServerProvisioningService.RebuildReport;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Comando /rebuild-server: apaga e reconstrói o servidor do zero a partir do template.
 *
 * Modos:
 *  - bot:  apaga só o que o bot criou (equivale a /reset-server + /setup-server)
 *  - tudo: apaga TODOS os canais e cargos do servidor e recria apenas o que está no template
 *
 * Segurança: o template é validado antes de qualquer exclusão; a confirmação é por botão e, no modo "tudo",
 * é preciso digitar o nome do servidor. Como o canal onde o comando foi usado provavelmente será apagado,
 * o resultado também é enviado no novo canal de logs e na DM de quem executou.
 */
public class RebuildServerCommand extends ListenerAdapter implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RebuildServerCommand.class);

    private static final String BTN_CONFIRM_PREFIX = "btn_rebuild_confirm_";
    private static final String BTN_CANCEL_PREFIX = "btn_rebuild_cancel_";
    private static final String MODAL_PREFIX = "modal_rebuild_";
    private static final String MODE_BOT = "bot";
    private static final String MODE_ALL = "tudo";
    /** Usado pelo /setup-server: limpeza completa, confirmada só pelo botão. */
    public static final String MODE_SETUP = "setup";

    private final ServerProvisioningService provisioningService;
    private final AuditLogService auditLogService;
    private final TemplateService templateService;

    public RebuildServerCommand(ServerProvisioningService provisioningService, AuditLogService auditLogService, TemplateService templateService) {
        this.provisioningService = provisioningService;
        this.auditLogService = auditLogService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("rebuild-server", "Apaga e reconstrói o servidor do zero a partir do template")
                .addOptions(new OptionData(OptionType.STRING, "modo", "O que apagar antes de reconstruir", true)
                        .addChoice("Só o que o bot criou", MODE_BOT)
                        .addChoice("TUDO (todos os canais e cargos do servidor)", MODE_ALL))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando só pode ser executado dentro de um servidor!").setEphemeral(true).queue();
            return;
        }
        String mode = MODE_ALL.equals(event.getOption("modo", MODE_BOT, o -> o.getAsString())) ? MODE_ALL : MODE_BOT;
        prompt(event, mode);
    }

    /**
     * Valida o template e mostra a tela de confirmação. Também usado pelo /setup-server (modo {@link #MODE_SETUP}).
     */
    public void prompt(SlashCommandInteractionEvent event, String mode) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando só pode ser executado dentro de um servidor!").setEphemeral(true).queue();
            return;
        }

        // Validar o template ANTES de oferecer a exclusão: se o JSON estiver quebrado, nada é apagado
        ServerTemplate template;
        try {
            template = templateService.reload(guild);
        } catch (Exception e) {
            event.reply("❌ O template tem um erro, então nada será apagado:\n```" + truncate(e.getMessage(), 1800) + "```")
                    .setEphemeral(true).queue();
            return;
        }
        List<String> problems = template.validate();

        boolean all = !MODE_BOT.equals(mode);
        boolean typedConfirmation = MODE_ALL.equals(mode);
        long channels = template.allChannels().count();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(all ? "☢️ Reconstruir o servidor INTEIRO do zero" : "♻️ Reconstruir o servidor do zero")
                .setColor(new Color(all ? 0x991B1B : 0xE74C3C))
                .setDescription((all
                        ? "**Serão apagados TODOS os canais, categorias e cargos do servidor** — inclusive os que não foram criados pelo bot.\n"
                        + "Ficam apenas: @everyone, cargos de integrações/boost, cargos acima do bot e canais exigidos pela Comunidade.\n\n"
                        : "Serão apagados todos os canais, categorias e cargos **criados pelo bot** (e o que estiver dentro dessas categorias).\n\n")
                        + "**Depois será criado:** " + template.getRoles().size() + " cargos, "
                        + template.getCategories().size() + " categorias e " + channels + " canais do template `"
                        + template.getTemplateName() + "`.\n\n"
                        + "⚠️ Mensagens dos canais apagados são perdidas para sempre. **Esta ação é irreversível.**"
                        + (typedConfirmation ? "\n\nAo confirmar, você precisará digitar o nome do servidor." : ""))
                .setFooter("O resultado será enviado no novo canal de logs e na sua DM", event.getUser().getEffectiveAvatarUrl())
                .setTimestamp(Instant.now());
        if (!problems.isEmpty()) {
            embed.addField("📝 Avisos do template", truncate("• " + String.join("\n• ", problems), 1000), false);
        }

        String userId = event.getUser().getId();
        event.replyEmbeds(embed.build())
                .addActionRow(
                        Button.danger(BTN_CONFIRM_PREFIX + mode + "_" + userId, all ? "Apagar TUDO e reconstruir" : "Apagar e reconstruir"),
                        Button.secondary(BTN_CANCEL_PREFIX + userId, "Cancelar"))
                .queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(BTN_CONFIRM_PREFIX) && !id.startsWith(BTN_CANCEL_PREFIX)) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("⛔ Apenas administradores podem usar este botão.").setEphemeral(true).queue();
            return;
        }

        if (id.startsWith(BTN_CANCEL_PREFIX)) {
            if (!id.endsWith("_" + member.getId())) {
                event.reply("⛔ Apenas quem executou o comando pode cancelar.").setEphemeral(true).queue();
                return;
            }
            event.editMessageEmbeds(new EmbedBuilder()
                            .setTitle("❌ Reconstrução cancelada")
                            .setColor(new Color(0x95A5A6))
                            .setDescription("Nada foi alterado.")
                            .setTimestamp(Instant.now()).build())
                    .setComponents(Collections.emptyList()).queue();
            return;
        }

        // btn_rebuild_confirm_<modo>_<userId>
        String[] parts = id.substring(BTN_CONFIRM_PREFIX.length()).split("_", 2);
        if (parts.length != 2 || !parts[1].equals(member.getId())) {
            event.reply("⛔ Apenas quem executou o comando pode confirmar.").setEphemeral(true).queue();
            return;
        }

        if (MODE_ALL.equals(parts[0])) {
            TextInput input = TextInput.create("confirm", "Digite o nome do servidor para confirmar", TextInputStyle.SHORT)
                    .setPlaceholder(guild.getName())
                    .setRequired(true)
                    .setMaxLength(100)
                    .build();
            event.replyModal(Modal.create(MODAL_PREFIX + member.getId(), "Apagar TUDO do servidor")
                    .addComponents(ActionRow.of(input))
                    .build()).queue();
            return;
        }

        boolean fullWipe = MODE_SETUP.equals(parts[0]);
        event.editMessageEmbeds(progressEmbed(fullWipe)).setComponents(Collections.emptyList()).queue();
        start(guild, member, event.getHook(), fullWipe);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL_PREFIX)) return;
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null || !member.hasPermission(Permission.ADMINISTRATOR)
                || !event.getModalId().equals(MODAL_PREFIX + member.getId())) {
            event.reply("⛔ Confirmação inválida.").setEphemeral(true).queue();
            return;
        }

        ModalMapping typed = event.getValue("confirm");
        if (typed == null || !typed.getAsString().trim().equals(guild.getName().trim())) {
            event.reply("❌ O nome digitado não confere com o nome do servidor. Nada foi apagado.").setEphemeral(true).queue();
            return;
        }

        if (event.getMessage() != null) {
            event.editMessageEmbeds(progressEmbed(true)).setComponents(Collections.emptyList()).queue();
        } else {
            event.replyEmbeds(progressEmbed(true)).queue();
        }
        start(guild, member, event.getHook(), true);
    }

    private void start(Guild guild, Member member, InteractionHook hook, boolean fullWipe) {
        User user = member.getUser();
        LOGGER.warn("Reconstrução {} confirmada na guilda {} por {}", fullWipe ? "COMPLETA" : "do bot", guild.getName(), user.getName());

        ServerTemplate template;
        try {
            template = templateService.reload(guild);
        } catch (Exception e) {
            hook.sendMessage("❌ O template ficou inválido desde a confirmação; nada foi apagado: " + truncate(e.getMessage(), 1500)).queue();
            return;
        }

        guild.getJDA().getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.watching(template.getBranding().getActivity()));
        provisioningService.rebuildServer(guild, template, fullWipe)
                .thenAccept(report -> {
                    MessageEmbed result = resultEmbed(guild, template, report, fullWipe);
                    deliver(guild, user, hook, result);
                    auditLogService.logSetupExecution(guild, member,
                            fullWipe ? "Reconstrução completa (/rebuild-server tudo)" : "Reconstrução (/rebuild-server bot)",
                            report.provisioning().rolesCreated() + report.provisioning().rolesUpdated(),
                            report.provisioning().categoriesCreated() + report.provisioning().categoriesUpdated(),
                            report.provisioning().channelsCreated() + report.provisioning().channelsUpdated());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Falha na reconstrução do servidor", ex);
                    MessageEmbed error = new EmbedBuilder()
                            .setTitle("❌ A reconstrução falhou no meio do caminho")
                            .setColor(new Color(0xE74C3C))
                            .setDescription("Erro: `" + truncate(String.valueOf(ex.getMessage()), 500) + "`\n\n"
                                    + "Rode `/sync-server` para terminar de criar o que faltou e `/server-status` para conferir.")
                            .setTimestamp(Instant.now()).build();
                    deliver(guild, user, hook, error);
                    return null;
                });
    }

    /**
     * O canal original provavelmente foi apagado: tentar a resposta da interação, o novo canal de logs e a DM.
     */
    private void deliver(Guild guild, User user, InteractionHook hook, MessageEmbed embed) {
        hook.sendMessageEmbeds(embed).queue(null, err -> LOGGER.debug("Canal original não existe mais"));
        auditLogService.findLogChannel(guild).ifPresent(ch -> ch.sendMessageEmbeds(embed).queue(null, err -> { }));
        user.openPrivateChannel()
                .flatMap(dm -> dm.sendMessageEmbeds(embed))
                .queue(null, err -> LOGGER.debug("DM fechada para {}", user.getName()));
    }

    private MessageEmbed resultEmbed(Guild guild, ServerTemplate template, RebuildReport report, boolean fullWipe) {
        var prov = report.provisioning();
        var wipe = report.wipe();
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle("✨ Servidor reconstruído do zero!")
                .setDescription("**" + guild.getName() + "** foi " + (fullWipe ? "completamente limpo" : "limpo")
                        + " e reconstruído com o template `" + template.getTemplateName() + "`.")
                .addField("🗑️ Apagados", wipe.channelsDeleted() + " canais · " + wipe.rolesDeleted() + " cargos", true)
                .addField("🛡️ Cargos criados", String.valueOf(prov.rolesCreated() + prov.rolesUpdated()), true)
                .addField("💬 Canais criados", String.valueOf(prov.channelsCreated() + prov.channelsUpdated()), true)
                .addField("🧩 Painéis", String.valueOf(prov.panelsPublished()), true)
                .addField("🛡️ AutoMod", prov.automodRules() + " regra(s)", true)
                .addField("⏱️ Tempo de criação", prov.executionTimeMs() / 1000 + " s", true);
        if (!wipe.kept().isEmpty()) {
            embed.addField("📌 Mantidos (o Discord não permite apagar)", truncate(String.join("\n", wipe.kept()), 1000), false);
        }
        if (prov.errorCount() > 0) {
            embed.addField("⚠️ Erros", prov.errorCount() + " item(ns) falharam — rode `/sync-server` e veja o console do bot.", false);
        }
        embed.addField("👉 Próximos passos", "1. `/server-status`\n2. Dê o cargo de membro à staff\n"
                + "3. Membros antigos precisam se verificar de novo (os cargos foram recriados)", false);
        return embed.build();
    }

    private static MessageEmbed progressEmbed(boolean fullWipe) {
        return new EmbedBuilder()
                .setTitle("⏳ Reconstruindo o servidor...")
                .setColor(new Color(0xE67E22))
                .setDescription((fullWipe ? "Apagando todos os canais e cargos" : "Apagando o que o bot criou")
                        + " e recriando tudo a partir do template.\n\n"
                        + "Este canal provavelmente será apagado. O resultado chegará no novo canal de logs e na sua DM.")
                .setTimestamp(Instant.now())
                .build();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
