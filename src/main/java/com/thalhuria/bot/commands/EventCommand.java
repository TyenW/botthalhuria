package com.thalhuria.bot.commands;

import com.thalhuria.bot.service.EventService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * /evento criar (evento nativo + anúncio + lembrete) e /evento listar.
 */
public class EventCommand implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventCommand.class);
    private final EventService eventService;
    private final TemplateService templateService;

    public EventCommand(EventService eventService, TemplateService templateService) {
        this.eventService = eventService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("evento", "Eventos agendados do servidor")
                .addSubcommands(
                        new SubcommandData("criar", "Cria um evento, anuncia e agenda o lembrete")
                                .addOptions(new OptionData(OptionType.STRING, "nome", "Nome do evento", true).setMaxLength(100))
                                .addOptions(new OptionData(OptionType.STRING, "inicio", "Data e hora: 25/12/2026 20:00 (ou 25/12 20:00)", true))
                                .addOptions(new OptionData(OptionType.INTEGER, "duracao", "Duração em minutos (padrão: 120)", false).setRequiredRange(15, 1440))
                                .addOptions(new OptionData(OptionType.STRING, "local", "Onde acontece (ex.: Arena 01, Twitch...)", false).setMaxLength(100))
                                .addOptions(new OptionData(OptionType.STRING, "descricao", "Detalhes do evento", false).setMaxLength(1000)),
                        new SubcommandData("listar", "Mostra os próximos eventos"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_EVENTS))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        if ("listar".equals(event.getSubcommandName())) {
            List<ScheduledEvent> events = eventService.upcoming(guild);
            EmbedBuilder embed = templateService.brandedEmbed(guild).setTitle("📅 Próximos eventos");
            if (events.isEmpty()) embed.setDescription(templateService.msg(guild, "event.none"));
            events.stream().limit(10).forEach(e -> embed.addField(e.getName(),
                    "<t:" + e.getStartTime().toEpochSecond() + ":F> · [ver evento](" + EventService.link(e) + ")", false));
            event.replyEmbeds(embed.build()).queue();
            return;
        }

        String input = event.getOption("inicio", "", o -> o.getAsString());
        Optional<OffsetDateTime> start = eventService.parse(guild, input);
        if (start.isEmpty()) {
            event.reply(templateService.msg(guild, "event.bad.date", "input", input)).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        CompletableFuture.supplyAsync(() -> eventService.create(member,
                        event.getOption("nome", "Evento", o -> o.getAsString()),
                        event.getOption("descricao", null, o -> o.getAsString()),
                        start.get(),
                        event.getOption("duracao", 120, o -> o.getAsInt()),
                        event.getOption("local", null, o -> o.getAsString())))
                .thenAccept(msg -> event.getHook().sendMessage(msg).queue())
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao criar evento", ex);
                    event.getHook().sendMessage("❌ Não foi possível criar o evento: " + ex.getMessage()).queue();
                    return null;
                });
    }
}
