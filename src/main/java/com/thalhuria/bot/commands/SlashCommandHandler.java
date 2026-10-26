package com.thalhuria.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public interface SlashCommandHandler {
    SlashCommandData getCommandData();

    void handle(SlashCommandInteractionEvent event);

    /**
     * Handlers que atendem mais de um comando (ex.: /daily, /saldo, /pagar) sobrescrevem este método;
     * {@link #handle} recebe o evento de qualquer um deles.
     */
    default List<SlashCommandData> getAllCommandData() {
        return List.of(getCommandData());
    }
}
