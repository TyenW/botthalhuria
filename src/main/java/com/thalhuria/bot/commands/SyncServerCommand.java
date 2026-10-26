package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.service.AuditLogService;
import com.thalhuria.bot.service.ServerProvisioningService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Comando /sync-server: Compara a guilda com o template, criando apenas o que
 * está ausente e atualizando permissões sem duplicar elementos existentes.
 */
public class SyncServerCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncServerCommand.class);
    private final ServerProvisioningService provisioningService;
    private final AuditLogService auditLogService;
    private final TemplateService templateService;

    public SyncServerCommand(ServerProvisioningService provisioningService, AuditLogService auditLogService, TemplateService templateService) {
        this.provisioningService = provisioningService;
        this.auditLogService = auditLogService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("sync-server", "Aplica as mudanças do template sem apagar nada")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN, "simular",
                        "Só mostrar o que mudaria, sem alterar o servidor", false)
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

        event.deferReply().queue();
        LOGGER.info("Executando /sync-server na Guild: {} por {}", guild.getName(), event.getUser().getName());

        ServerTemplate template;
        try {
            template = templateService.reload(guild);
        } catch (Exception e) {
            event.getHook().sendMessage("❌ O arquivo de template tem um erro e nada foi alterado:\n```" + truncate(e.getMessage(), 1800) + "```").queue();
            return;
        }
        List<String> problems = template.validate();

        if (event.getOption("simular", false, o -> o.getAsBoolean())) {
            List<String> plan = provisioningService.previewSync(guild, template);
            EmbedBuilder preview = templateService.brandedEmbed(guild)
                    .setTitle("🔍 Simulação do /sync-server")
                    .setDescription(truncate(plan.size() <= 1 ? "✅ O servidor já está igual ao template.\n" + String.join("\n", plan)
                            : String.join("\n", plan), 4000))
                    .setFooter("Nada foi alterado. Rode /sync-server sem 'simular' para aplicar.");
            if (!problems.isEmpty()) {
                preview.addField("📝 Avisos do template", truncate("• " + String.join("\n• ", problems), 1000), false);
            }
            event.getHook().sendMessageEmbeds(preview.build()).queue();
            return;
        }

        guild.getJDA().getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.watching(template.getBranding().getActivity()));
        provisioningService.provisionServer(guild, template, true)
                .thenAccept(report -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("🔄 Sincronização Concluída!")
                            .setColor(new Color(0x3498DB))
                            .setDescription("O servidor foi verificado e sincronizado com o template `" + template.getTemplateName() + "` de forma idempotente.")
                            .addField("🛡️ Cargos Novos / Atualizados", String.format("+%d novos | %d verificados", report.rolesCreated(), report.rolesUpdated()), true)
                            .addField("📁 Categorias Novas / Atualizadas", String.format("+%d novas | %d verificadas", report.categoriesCreated(), report.categoriesUpdated()), true)
                            .addField("💬 Canais Novos / Atualizados", String.format("+%d novos | %d verificados", report.channelsCreated(), report.channelsUpdated()), true)
                            .addField("🧩 Painéis", "**" + report.panelsPublished() + "** publicados/atualizados", true)
                            .addField("🛡️ AutoMod", "**" + report.automodRules() + "** regra(s) ativa(s)", true)
                            .addField("⏱️ Tempo de Sincronização", report.executionTimeMs() + " ms", true)
                            .setFooter("Bot Thaluria • Sincronização Idempotente", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                            .setTimestamp(Instant.now());

                    if (report.errorCount() > 0) {
                        embed.addField("⚠️ Erros", report.errorCount() + " item(ns) falharam — veja o console do bot.", false);
                    }
                    if (!problems.isEmpty()) {
                        embed.addField("📝 Avisos do template", truncate("• " + String.join("\n• ", problems), 1000), false);
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();

                    auditLogService.logSetupExecution(
                            guild,
                            event.getMember(),
                            "Sincronização Idempotente (/sync-server)",
                            report.rolesCreated() + report.rolesUpdated(),
                            report.categoriesCreated() + report.categoriesUpdated(),
                            report.channelsCreated() + report.channelsUpdated()
                    );
                })
                .exceptionally(ex -> {
                    LOGGER.error("Falha ao executar /sync-server", ex);
                    event.getHook().sendMessage("❌ Falha na sincronização: " + ex.getMessage()).queue();
                    return null;
                });
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
