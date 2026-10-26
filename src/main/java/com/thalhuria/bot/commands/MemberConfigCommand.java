package com.thalhuria.bot.commands;

import com.thalhuria.bot.service.AuditLogService;
import com.thalhuria.bot.service.MemberManagementService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;

/**
 * Comando Slash /membro configurar:
 * Utiliza o seletor nativo OptionType.USER para escolher um membro do servidor,
 * atribui automaticamente o cargo principal e cria opcionalmente uma sala privada individual.
 */
public class MemberConfigCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberConfigCommand.class);
    private final MemberManagementService memberManagementService;
    private final AuditLogService auditLogService;

    public MemberConfigCommand(MemberManagementService memberManagementService, AuditLogService auditLogService) {
        this.memberManagementService = memberManagementService;
        this.auditLogService = auditLogService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("membro", "Gerenciamento de membros e salas privadas")
                .addSubcommands(
                        new SubcommandData("configurar", "Atribui cargo base e cria canal privado para o usuário")
                                .addOption(OptionType.USER, "usuario", "Selecione o membro na lista do servidor", true)
                                .addOption(OptionType.BOOLEAN, "criar_sala", "Criar sala privada individual exclusiva? (Padrão: Sim)", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Este comando só pode ser utilizado em um servidor!").setEphemeral(true).queue();
            return;
        }

        // Validação de subcomando
        String subcommand = event.getSubcommandName();
        if (subcommand != null && !subcommand.equalsIgnoreCase("configurar")) {
            event.reply("Subcomando não reconhecido.").setEphemeral(true).queue();
            return;
        }

        OptionMapping userOpt = event.getOption("usuario");
        if (userOpt == null) {
            event.reply("Você precisa selecionar um usuário!").setEphemeral(true).queue();
            return;
        }

        Member targetMember = userOpt.getAsMember();
        if (targetMember == null) {
            event.reply("O usuário selecionado não é um membro válido deste servidor.").setEphemeral(true).queue();
            return;
        }

        OptionMapping criarSalaOpt = event.getOption("criar_sala");
        boolean criarSala = criarSalaOpt == null || criarSalaOpt.getAsBoolean();

        event.deferReply().queue();
        LOGGER.info("Configurando membro {} (criarSala={}) por {}", targetMember.getEffectiveName(), criarSala, event.getUser().getName());

        memberManagementService.configureMember(guild, targetMember, criarSala)
                .thenAccept(result -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("✅ Membro Configurado com Sucesso!")
                            .setColor(new Color(0x1ABC9C))
                            .setThumbnail(targetMember.getUser().getEffectiveAvatarUrl())
                            .setDescription("O processo de configuração do membro foi executado com sucesso.")
                            .addField("👤 Usuário Configurado", targetMember.getAsMention() + " (`" + targetMember.getId() + "`)", false)
                            .addField("🛡️ Cargo Atribuído", result.role() != null ? result.role().getAsMention() : "Nenhum cargo configurado", true);

                    if (result.privateChannel() != null) {
                        embed.addField("🚪 Sala Privada Criada", result.privateChannel().getAsMention(), true);
                        embed.addField("🔒 Permissões da Sala", "Visível exclusivamente para " + targetMember.getAsMention() + " e Administradores.", false);
                    } else {
                        embed.addField("🚪 Sala Privada", "Criação não solicitada ou ignorada", true);
                    }

                    embed.setFooter("Gerenciamento de Membros • Bot Thaluria", guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
                         .setTimestamp(Instant.now());

                    event.getHook().sendMessageEmbeds(embed.build()).queue();

                    // Registrar no canal de auditoria
                    auditLogService.logMemberConfiguration(
                            guild,
                            event.getMember(),
                            targetMember,
                            result.role(),
                            result.privateChannel()
                    );
                })
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao configurar membro", ex);
                    event.getHook().sendMessage("❌ Erro ao configurar membro: " + ex.getMessage()).queue();
                    return null;
                });
    }
}
