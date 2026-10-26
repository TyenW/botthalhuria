package com.thalhuria.bot.commands;

import com.thalhuria.bot.service.OnboardingService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /boas-vindas: reenvia a mensagem de boas-vindas (com o cartão) marcando o membro.
 * Exclusivo do dono do servidor — o menu só mostra o comando para administradores,
 * e a execução confere se quem chamou é o dono.
 */
public class WelcomeCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeCommand.class);
    private final OnboardingService onboardingService;
    private final TemplateService templateService;

    public WelcomeCommand(OnboardingService onboardingService, TemplateService templateService) {
        this.onboardingService = onboardingService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("boas-vindas", "Reenvia as boas-vindas marcando o membro (somente o dono do servidor)")
                .addOption(OptionType.USER, "usuario", "Membro que vai receber as boas-vindas", true)
                .addOption(OptionType.BOOLEAN, "dm", "Também reenviar a mensagem privada de boas-vindas? (padrão: não)", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member caller = event.getMember();
        if (guild == null || caller == null) return;

        if (!caller.isOwner()) {
            event.reply(templateService.msg(guild, "welcome.resend.owner.only")).setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("usuario", null, o -> o.getAsMember());
        if (target == null || target.getUser().isBot()) {
            event.reply(templateService.msg(guild, "welcome.resend.invalid")).setEphemeral(true).queue();
            return;
        }

        boolean dm = event.getOption("dm", false, o -> o.getAsBoolean());
        event.deferReply(true).queue();
        onboardingService.resendWelcome(target, dm)
                .thenAccept(msg -> event.getHook().sendMessage(msg).queue())
                .exceptionally(ex -> {
                    LOGGER.error("Falha ao reenviar boas-vindas", ex);
                    event.getHook().sendMessage("❌ Não foi possível reenviar as boas-vindas: " + ex.getMessage()).queue();
                    return null;
                });
    }
}
