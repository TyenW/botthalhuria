package com.thalhuria.bot.service;

import com.thalhuria.bot.config.EventsConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Eventos agendados nativos do Discord: criação por comando, anúncio no canal configurado
 * (marcando o cargo de eventos) e lembrete automático X minutos antes do início.
 */
public class EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TemplateService templateService;
    private final ScheduledExecutorService scheduler;
    private final Set<String> scheduledReminders = ConcurrentHashMap.newKeySet();

    public EventService(TemplateService templateService, ScheduledExecutorService scheduler) {
        this.templateService = templateService;
        this.scheduler = scheduler;
    }

    /**
     * Converte "25/12/2026 20:00" (ou "25/12 20:00", ano atual) no fuso configurado.
     */
    public Optional<OffsetDateTime> parse(Guild guild, String input) {
        return parseDate(input, zone(guild));
    }

    public static Optional<OffsetDateTime> parseDate(String input, ZoneId zone) {
        String text = input.trim();
        if (text.matches("\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}")) {
            String[] parts = text.split(" ");
            text = parts[0] + "/" + LocalDateTime.now(zone).getYear() + " " + parts[1];
        }
        text = text.replaceAll("^(\\d)/", "0$1/").replaceAll("/(\\d)/", "/0$1/").replaceAll(" (\\d):", " 0$1:");
        try {
            return Optional.of(LocalDateTime.parse(text, INPUT).atZone(zone).toOffsetDateTime());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Cria o evento, anuncia e agenda o lembrete. Bloqueante.
     */
    public String create(Member creator, String name, String description, OffsetDateTime start, int durationMinutes, String location) {
        Guild guild = creator.getGuild();
        EventsConfig config = templateService.get(guild).getEvents();
        if (!config.isEnabled()) return templateService.msg(guild, "event.disabled");
        if (start.isBefore(OffsetDateTime.now().plusMinutes(1))) return templateService.msg(guild, "event.past");

        OffsetDateTime end = start.plusMinutes(Math.max(15, durationMinutes));
        ScheduledEvent event = guild.createScheduledEvent(name, location == null || location.isBlank() ? guild.getName() : location, start, end)
                .setDescription(description == null || description.isBlank() ? null : description)
                .complete();

        announce(guild, event, config, creator);
        scheduleReminder(event);
        return templateService.msg(guild, "event.created", "name", name, "link", link(event));
    }

    /**
     * Reagenda lembretes dos eventos futuros (chamado quando o bot conecta).
     */
    public void restore(Guild guild) {
        if (!templateService.get(guild).getEvents().isEnabled()) return;
        for (ScheduledEvent event : guild.getScheduledEvents()) {
            if (event.getStatus() == ScheduledEvent.Status.SCHEDULED) scheduleReminder(event);
        }
    }

    public List<ScheduledEvent> upcoming(Guild guild) {
        return guild.getScheduledEvents().stream()
                .filter(e -> e.getStatus() == ScheduledEvent.Status.SCHEDULED || e.getStatus() == ScheduledEvent.Status.ACTIVE)
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .toList();
    }

    private void announce(Guild guild, ScheduledEvent event, EventsConfig config, Member creator) {
        Optional<GuildMessageChannel> channel = templateService.messageChannel(guild, config.getAnnounceChannelKey());
        if (channel.isEmpty()) return;
        Optional<Role> ping = templateService.role(guild, config.getPingRoleKey());

        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle("📅 " + event.getName())
                .setDescription(event.getDescription() != null ? event.getDescription() : "")
                .addField("🕒 Quando", "<t:" + event.getStartTime().toEpochSecond() + ":F>\n<t:" + event.getStartTime().toEpochSecond() + ":R>", true)
                .addField("📍 Onde", event.getLocation(), true)
                .addField("🙋 Organização", creator.getAsMention(), true)
                .addField("🔗 Inscreva-se", "[Clique em \"Interessado\" no evento](" + link(event) + ")", false);
        channel.get().sendMessage(ping.map(Role::getAsMention).orElse(""))
                .setEmbeds(embed.build())
                .setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
                .queue(null, err -> LOGGER.warn("Falha ao anunciar evento: {}", err.getMessage()));
    }

    private void scheduleReminder(ScheduledEvent event) {
        if (!scheduledReminders.add(event.getId())) return;
        Guild guild = event.getGuild();
        int minutes = templateService.get(guild).getEvents().getReminderMinutes();
        long delay = Duration.between(OffsetDateTime.now(), event.getStartTime().minusMinutes(minutes)).toSeconds();
        if (delay < 0) return;

        scheduler.schedule(() -> {
            ScheduledEvent current = guild.getScheduledEventById(event.getId());
            if (current == null || current.getStatus() != ScheduledEvent.Status.SCHEDULED) return;
            EventsConfig config = templateService.get(guild).getEvents();
            templateService.messageChannel(guild, config.getAnnounceChannelKey()).ifPresent(ch -> {
                String ping = templateService.role(guild, config.getPingRoleKey()).map(Role::getAsMention).orElse("");
                ch.sendMessage(ping + " " + templateService.msg(guild, "event.reminder",
                                "name", current.getName(), "minutes", minutes, "time", "<t:" + current.getStartTime().toEpochSecond() + ":R>",
                                "link", link(current)))
                        .setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
                        .queue(null, err -> { });
            });
        }, delay, TimeUnit.SECONDS);
        LOGGER.info("Lembrete do evento '{}' agendado para daqui {} min", event.getName(), delay / 60);
    }

    public static String link(ScheduledEvent event) {
        return "https://discord.com/events/" + event.getGuild().getId() + "/" + event.getId();
    }

    private ZoneId zone(Guild guild) {
        try {
            return ZoneId.of(templateService.get(guild).getEvents().getTimezone());
        } catch (Exception e) {
            return ZoneId.of("America/Sao_Paulo");
        }
    }
}
