package com.thalhuria.bot.listeners;

import com.thalhuria.bot.config.ChannelTemplate;
import com.thalhuria.bot.service.ActivityService;
import com.thalhuria.bot.service.EventService;
import com.thalhuria.bot.service.InviteService;
import com.thalhuria.bot.service.OnboardingService;
import com.thalhuria.bot.service.TempVoiceService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Comportamentos automáticos da comunidade definidos no template:
 * onboarding na entrada, reações/tópicos automáticos em canais de sugestão e salas de voz temporárias.
 */
public class CommunityListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityListener.class);

    private final TemplateService templateService;
    private final OnboardingService onboardingService;
    private final TempVoiceService tempVoiceService;
    private final ActivityService activityService;
    private final EventService eventService;
    private final InviteService inviteService;

    public CommunityListener(TemplateService templateService, OnboardingService onboardingService, TempVoiceService tempVoiceService,
                             ActivityService activityService, EventService eventService, InviteService inviteService) {
        this.templateService = templateService;
        this.onboardingService = onboardingService;
        this.tempVoiceService = tempVoiceService;
        this.activityService = activityService;
        this.eventService = eventService;
        this.inviteService = inviteService;
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        tempVoiceService.cleanup(event.getGuild());
        eventService.restore(event.getGuild());
        inviteService.load(event.getGuild());
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // Primeiro descobre o convite (para as boas-vindas mostrarem quem convidou), depois dá as boas-vindas
        inviteService.onJoin(event.getMember())
                .thenAccept(source -> onboardingService.handleJoin(event.getMember(), source));
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        inviteService.onLeave(event.getGuild(), event.getUser());
    }

    @Override
    public void onGuildInviteCreate(@NotNull GuildInviteCreateEvent event) {
        inviteService.onInviteCreate(event.getGuild(), event.getInvite());
    }

    @Override
    public void onGuildInviteDelete(@NotNull GuildInviteDeleteEvent event) {
        inviteService.onInviteDelete(event.getGuild(), event.getCode());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getChannelType() != ChannelType.TEXT) return;
        Message message = event.getMessage();
        if (message.getType() != MessageType.DEFAULT && message.getType() != MessageType.INLINE_REPLY) return;
        if (event.getMember() != null) {
            activityService.onMessage(event.getMember(), event.getGuildChannel());
        }
        if (message.getType() != MessageType.DEFAULT) return;

        Optional<ChannelTemplate> tpl = templateService.channelTemplateFor(event.getGuild(), event.getChannel().getId());
        if (tpl.isEmpty()) return;

        for (String reaction : tpl.get().getAutoReactions()) {
            try {
                message.addReaction(Emoji.fromFormatted(reaction)).queue(null, err -> { });
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Emoji inválido em autoReactions de {}: {}", tpl.get().getKey(), reaction);
            }
        }

        if (tpl.get().isAutoThread()) {
            String name = "💬 " + event.getMember().getEffectiveName();
            message.createThreadChannel(name.length() > 100 ? name.substring(0, 100) : name)
                    .queue(null, err -> LOGGER.debug("Não foi possível criar tópico: {}", err.getMessage()));
        }
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() != null) {
            tempVoiceService.handleJoin(event.getMember(), event.getChannelJoined());
        }
        if (event.getChannelLeft() != null) {
            tempVoiceService.handleLeave(event.getChannelLeft());
        }
    }
}
