package com.thalhuria.bot.service;

import com.thalhuria.bot.config.ChannelTemplate;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Canais contadores: canais de voz cujo nome mostra um número ao vivo ("👥 Membros: 1.234").
 * Atualizados a cada 10 minutos — o Discord permite só 2 renomeações por canal a cada 10 minutos.
 */
public class StatsCounterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsCounterService.class);
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));

    private final TemplateService templateService;

    public StatsCounterService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void updateAll(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            try {
                update(guild);
            } catch (Exception e) {
                LOGGER.warn("Falha ao atualizar contadores de {}: {}", guild.getName(), e.getMessage());
            }
        }
    }

    public void update(Guild guild) {
        List<ChannelTemplate> counters = templateService.get(guild).allChannels()
                .filter(c -> c.getCounter() != null)
                .toList();
        if (counters.isEmpty()) return;

        Integer online = null;
        for (ChannelTemplate tpl : counters) {
            var channel = templateService.channel(guild, tpl.getKey());
            if (channel.isEmpty() || !(channel.get() instanceof VoiceChannel vc)) continue;

            String type = tpl.getCounter().toUpperCase();
            long value;
            switch (type) {
                case "HUMANS" -> value = guild.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
                case "BOTS" -> value = guild.getMembers().stream().filter(m -> m.getUser().isBot()).count();
                case "BOOSTS" -> value = guild.getBoostCount();
                case "ONLINE" -> {
                    // Contagem aproximada do Discord: não exige a intent privilegiada de presença
                    if (online == null) online = guild.retrieveMetaData().complete().getApproximatePresences();
                    value = online;
                }
                default -> value = guild.getMemberCount();
            }

            String name = tpl.getName().replace("{value}", FORMAT.format(value));
            if (name.length() > 100) name = name.substring(0, 100);
            if (!vc.getName().equals(name)) {
                vc.getManager().setName(name).queue(null, err -> LOGGER.debug("Contador {} não atualizado: {}", tpl.getKey(), err.getMessage()));
            }
        }
    }
}
