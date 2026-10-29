package com.thalhuria.bot.service;

import com.thalhuria.bot.config.ChannelTemplate;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conferências feitas toda vez que o bot liga, para corrigir o que aconteceu enquanto ele estava desligado:
 * limites de pessoas dos canais de voz e cargo inicial de quem entrou nesse meio tempo.
 */
public class StartupCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupCheckService.class);

    private final TemplateService templateService;
    private final OnboardingService onboardingService;

    public StartupCheckService(TemplateService templateService, OnboardingService onboardingService) {
        this.templateService = templateService;
        this.onboardingService = onboardingService;
    }

    public void runAll(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            try {
                enforceVoiceLimits(guild);
                onboardingService.catchUpMissedJoins(guild);
            } catch (Exception e) {
                LOGGER.warn("Falha nas conferências de inicialização em {}: {}", guild.getName(), e.getMessage());
            }
        }
    }

    /**
     * Reaplica o userLimit do template nos canais de voz fixos (ex.: Duo = 2), caso alguém tenha mudado na mão.
     */
    private void enforceVoiceLimits(Guild guild) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) return;
        templateService.get(guild).allChannels()
                .filter(tpl -> "VOICE".equalsIgnoreCase(tpl.getType()) && !tpl.isJoinToCreate() && tpl.getCounter() == null)
                .forEach(tpl -> templateService.channel(guild, tpl.getKey())
                        .filter(VoiceChannel.class::isInstance)
                        .map(VoiceChannel.class::cast)
                        .filter(vc -> vc.getUserLimit() != tpl.getUserLimit())
                        .ifPresent(vc -> applyLimit(vc, tpl)));
    }

    private void applyLimit(VoiceChannel channel, ChannelTemplate tpl) {
        channel.getManager().setUserLimit(tpl.getUserLimit()).reason("Limite de pessoas do template").queue(
                ok -> LOGGER.info("Limite de {} ajustado para {}", channel.getName(), tpl.getUserLimit()),
                err -> LOGGER.warn("Falha ao ajustar limite de {}: {}", channel.getName(), err.getMessage()));
    }
}
