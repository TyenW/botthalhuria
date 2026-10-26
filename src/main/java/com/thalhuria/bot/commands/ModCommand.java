package com.thalhuria.bot.commands;

import com.thalhuria.bot.service.ModerationService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * /mod advertir | castigo | remover-castigo | expulsar | banir | limpar | historico | perdoar
 * Visível para quem tem "Castigar membros". Cada ação também confere a permissão específica do Discord.
 */
public class ModCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommand.class);

    private final ModerationService moderationService;
    private final TemplateService templateService;

    public ModCommand(ModerationService moderationService, TemplateService templateService) {
        this.moderationService = moderationService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        OptionData reason = new OptionData(OptionType.STRING, "motivo", "Motivo (fica no histórico e é enviado ao membro)", true).setMaxLength(500);
        return Commands.slash("mod", "Ferramentas de moderação")
                .addSubcommands(
                        new SubcommandData("advertir", "Registra uma advertência (com punição automática ao acumular)")
                                .addOption(OptionType.USER, "usuario", "Membro", true).addOptions(reason),
                        new SubcommandData("castigo", "Silencia o membro por um tempo (timeout nativo)")
                                .addOption(OptionType.USER, "usuario", "Membro", true)
                                .addOptions(new OptionData(OptionType.INTEGER, "minutos", "Duração em minutos (até 28 dias)", true)
                                        .setRequiredRange(1, 40320))
                                .addOptions(reason),
                        new SubcommandData("remover-castigo", "Encerra o castigo de um membro")
                                .addOption(OptionType.USER, "usuario", "Membro", true),
                        new SubcommandData("expulsar", "Expulsa o membro do servidor")
                                .addOption(OptionType.USER, "usuario", "Membro", true).addOptions(reason),
                        new SubcommandData("banir", "Bane o membro do servidor")
                                .addOption(OptionType.USER, "usuario", "Membro (ou ID de quem já saiu)", true).addOptions(reason)
                                .addOptions(new OptionData(OptionType.INTEGER, "apagar_dias", "Apagar mensagens dos últimos N dias (0-7)", false)
                                        .setRequiredRange(0, 7)),
                        new SubcommandData("limpar", "Apaga mensagens recentes deste canal")
                                .addOptions(new OptionData(OptionType.INTEGER, "quantidade", "Quantas mensagens (1-100)", true).setRequiredRange(1, 100))
                                .addOption(OptionType.USER, "usuario", "Apagar apenas mensagens deste membro", false),
                        new SubcommandData("historico", "Mostra as advertências e punições de um membro")
                                .addOption(OptionType.USER, "usuario", "Membro", true),
                        new SubcommandData("perdoar", "Remove uma infração do histórico ativo")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "Número da infração (veja em /mod historico)", true).setMinValue(1)))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member moderator = event.getMember();
        String sub = event.getSubcommandName();
        if (guild == null || moderator == null || sub == null) return;

        Member target = event.getOption("usuario", null, o -> o.getAsMember());
        User targetUser = event.getOption("usuario", null, o -> o.getAsUser());
        String reason = event.getOption("motivo", "Sem motivo informado", o -> o.getAsString());

        switch (sub) {
            case "advertir" -> requireMember(event, target, Permission.MODERATE_MEMBERS, () -> moderationService.warn(moderator, target, reason));
            case "castigo" -> requireMember(event, target, Permission.MODERATE_MEMBERS, () ->
                    moderationService.timeout(moderator, target, event.getOption("minutos", 10, o -> o.getAsInt()), reason));
            case "remover-castigo" -> requireMember(event, target, Permission.MODERATE_MEMBERS, () -> moderationService.untimeout(moderator, target));
            case "expulsar" -> requireMember(event, target, Permission.KICK_MEMBERS, () -> moderationService.kick(moderator, target, reason));
            case "banir" -> {
                if (targetUser == null) return;
                run(event, Permission.BAN_MEMBERS, () -> moderationService.ban(moderator, targetUser, reason,
                        event.getOption("apagar_dias", 0, o -> o.getAsInt())));
            }
            case "limpar" -> {
                if (!(event.getChannel() instanceof GuildMessageChannel channel)) return;
                run(event, Permission.MESSAGE_MANAGE, () -> moderationService.clear(moderator, channel,
                        event.getOption("quantidade", 10, o -> o.getAsInt()), targetUser));
            }
            case "historico" -> {
                if (targetUser == null) return;
                event.replyEmbeds(moderationService.history(guild, targetUser)).setEphemeral(true).queue();
            }
            case "perdoar" -> run(event, Permission.MODERATE_MEMBERS, () ->
                    moderationService.pardon(moderator, event.getOption("id", 0L, o -> o.getAsLong())));
            default -> event.reply("Subcomando desconhecido.").setEphemeral(true).queue();
        }
    }

    private void requireMember(SlashCommandInteractionEvent event, Member target, Permission permission, Supplier<String> action) {
        if (target == null) {
            event.reply(templateService.msg(event.getGuild(), "mod.not.member")).setEphemeral(true).queue();
            return;
        }
        run(event, permission, action);
    }

    private void run(SlashCommandInteractionEvent event, Permission permission, Supplier<String> action) {
        if (!event.getMember().hasPermission(permission)) {
            event.reply(templateService.msg(event.getGuild(), "mod.no.permission")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        CompletableFuture.supplyAsync(action)
                .thenAccept(msg -> event.getHook().sendMessage(msg).queue())
                .exceptionally(ex -> {
                    LOGGER.error("Erro no /mod {}", event.getSubcommandName(), ex);
                    event.getHook().sendMessage("❌ " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage())).queue();
                    return null;
                });
    }
}
