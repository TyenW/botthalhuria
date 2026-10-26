package com.thalhuria.bot.listeners;

import com.thalhuria.bot.service.EconomyService;
import com.thalhuria.bot.service.OnboardingService;
import com.thalhuria.bot.service.PanelService;
import com.thalhuria.bot.service.TicketService;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Trata os botões e menus dos painéis publicados pelo bot (verificação, cargos, tickets, loja).
 * O trabalho pesado roda fora da thread de eventos do JDA; a resposta é sempre efêmera.
 */
public class PanelInteractionListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PanelInteractionListener.class);
    public static final String SHOP_MENU_ID = "thal:shop";

    private final OnboardingService onboardingService;
    private final TicketService ticketService;
    private final EconomyService economyService;

    public PanelInteractionListener(OnboardingService onboardingService, TicketService ticketService, EconomyService economyService) {
        this.onboardingService = onboardingService;
        this.ticketService = ticketService;
        this.economyService = economyService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Member member = event.getMember();
        String id = event.getComponentId();
        if (member == null) return;

        if (id.equals(PanelService.VERIFY_BUTTON_ID)) {
            runEphemeral(event, () -> onboardingService.verify(member));
        } else if (id.equals(TicketService.OPEN_BUTTON_PREFIX) || id.startsWith(TicketService.OPEN_BUTTON_PREFIX + ":")) {
            String type = id.length() > TicketService.OPEN_BUTTON_PREFIX.length()
                    ? id.substring(TicketService.OPEN_BUTTON_PREFIX.length() + 1) : null;
            runEphemeral(event, () -> ticketService.open(member, type));
        } else if (id.startsWith(PanelService.ROLE_BUTTON_PREFIX)) {
            String roleKey = id.substring(PanelService.ROLE_BUTTON_PREFIX.length());
            runEphemeral(event, () -> onboardingService.toggleRole(member, roleKey));
        } else if (id.equals(TicketService.CLOSE_BUTTON_ID)) {
            if (!(event.getChannel() instanceof TextChannel channel)) return;
            ticketService.close(member, channel).ifPresentOrElse(
                    msg -> event.reply(msg).queue(),
                    () -> event.reply("Este canal não é um ticket.").setEphemeral(true).queue());
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        if (PanelService.SELF_ROLES_MENU_ID.equals(event.getComponentId())) {
            runEphemeral(event, () -> {
                var change = onboardingService.applySelfRoles(member, event.getValues());
                if (change.added().isEmpty() && change.removed().isEmpty()) {
                    return "Nada mudou — seus cargos já estavam assim. 👍";
                }
                StringBuilder sb = new StringBuilder("🎭 Cargos atualizados!");
                if (!change.added().isEmpty()) {
                    sb.append("\n➕ ").append(change.added().stream().map(IMentionable::getAsMention).collect(Collectors.joining(" ")));
                }
                if (!change.removed().isEmpty()) {
                    sb.append("\n➖ ").append(change.removed().stream().map(IMentionable::getAsMention).collect(Collectors.joining(" ")));
                }
                return sb.toString();
            });
        } else if (SHOP_MENU_ID.equals(event.getComponentId()) && !event.getValues().isEmpty()) {
            String item = event.getValues().get(0);
            runEphemeral(event, () -> economyService.buy(member, item));
        }
    }

    private void runEphemeral(IReplyCallback event, Supplier<String> action) {
        event.deferReply(true).queue();
        CompletableFuture.supplyAsync(action)
                .thenAccept(msg -> event.getHook().sendMessage(msg).queue())
                .exceptionally(ex -> {
                    LOGGER.error("Erro ao processar interação de painel", ex);
                    event.getHook().sendMessage("❌ Algo deu errado. Se persistir, avise a staff.").queue();
                    return null;
                });
    }
}
