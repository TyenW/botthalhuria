package com.thalhuria.bot.listeners;

import com.thalhuria.bot.service.TempVoiceService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Botões, formulários e menu do painel de controle das salas de voz temporárias. Só o dono da sala pode usar.
 */
public class TempVoiceControlListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TempVoiceControlListener.class);
    private static final String MODAL_RENAME = "thal:tv:modal:rename";
    private static final String MODAL_LIMIT = "thal:tv:modal:limit";
    private static final String SELECT_TRANSFER = "thal:tv:select:transfer";

    private final TempVoiceService tempVoiceService;
    private final TemplateService templateService;

    public TempVoiceControlListener(TempVoiceService tempVoiceService, TemplateService templateService) {
        this.tempVoiceService = tempVoiceService;
        this.templateService = templateService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("thal:tv:")) return;
        VoiceChannel channel = ownedChannel(event.getChannel(), event.getMember());
        if (channel == null) {
            event.reply(templateService.msg(event.getGuild(), "tempvoice.not.owner")).setEphemeral(true).queue();
            return;
        }

        switch (id) {
            case TempVoiceService.BTN_LOCK -> runToggle(event, channel, Permission.VOICE_CONNECT, "tempvoice.locked", "tempvoice.unlocked");
            case TempVoiceService.BTN_HIDE -> runToggle(event, channel, Permission.VIEW_CHANNEL, "tempvoice.hidden", "tempvoice.visible");
            case TempVoiceService.BTN_RENAME -> event.replyModal(Modal.create(MODAL_RENAME, "Renomear sala")
                    .addComponents(ActionRow.of(TextInput.create("name", "Novo nome", TextInputStyle.SHORT)
                            .setValue(channel.getName()).setMaxLength(100).setRequired(true).build()))
                    .build()).queue();
            case TempVoiceService.BTN_LIMIT -> event.replyModal(Modal.create(MODAL_LIMIT, "Limite de pessoas")
                    .addComponents(ActionRow.of(TextInput.create("limit", "Máximo de pessoas (0 = sem limite)", TextInputStyle.SHORT)
                            .setValue(String.valueOf(channel.getUserLimit())).setMaxLength(2).setRequired(true).build()))
                    .build()).queue();
            case TempVoiceService.BTN_TRANSFER -> event.reply(templateService.msg(event.getGuild(), "tempvoice.transfer.pick"))
                    .addActionRow(EntitySelectMenu.create(SELECT_TRANSFER, EntitySelectMenu.SelectTarget.USER)
                            .setPlaceholder("Novo dono da sala").build())
                    .setEphemeral(true).queue();
            default -> { }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("thal:tv:modal:")) return;
        VoiceChannel channel = ownedChannel(event.getChannel(), event.getMember());
        if (channel == null) {
            event.reply(templateService.msg(event.getGuild(), "tempvoice.not.owner")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        CompletableFuture.runAsync(() -> {
            if (MODAL_RENAME.equals(event.getModalId())) {
                String name = event.getValue("name").getAsString().trim();
                tempVoiceService.rename(channel, name);
                event.getHook().sendMessage(templateService.msg(event.getGuild(), "tempvoice.renamed", "name", name)).queue();
            } else {
                int limit;
                try {
                    limit = Integer.parseInt(event.getValue("limit").getAsString().trim());
                } catch (NumberFormatException e) {
                    event.getHook().sendMessage(templateService.msg(event.getGuild(), "tempvoice.limit.invalid")).queue();
                    return;
                }
                tempVoiceService.setLimit(channel, limit);
                event.getHook().sendMessage(templateService.msg(event.getGuild(), "tempvoice.limit.ok", "limit",
                        limit <= 0 ? "sem limite" : String.valueOf(Math.min(99, limit)))).queue();
            }
        }).exceptionally(ex -> fail(event.getHook(), ex));
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        if (!SELECT_TRANSFER.equals(event.getComponentId())) return;
        VoiceChannel channel = ownedChannel(event.getChannel(), event.getMember());
        if (channel == null || event.getMentions().getMembers().isEmpty()) {
            event.reply(templateService.msg(event.getGuild(), "tempvoice.not.owner")).setEphemeral(true).queue();
            return;
        }
        Member newOwner = event.getMentions().getMembers().get(0);
        if (newOwner.getUser().isBot() || !channel.getMembers().contains(newOwner)) {
            event.reply(templateService.msg(event.getGuild(), "tempvoice.transfer.invalid")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        CompletableFuture.runAsync(() -> {
            tempVoiceService.transfer(channel, event.getMember(), newOwner);
            event.getHook().sendMessage(templateService.msg(event.getGuild(), "tempvoice.transfer.ok", "user", newOwner.getAsMention())).queue();
            channel.sendMessage(templateService.msg(event.getGuild(), "tempvoice.transfer.announce", "user", newOwner.getAsMention())).queue(null, err -> { });
        }).exceptionally(ex -> fail(event.getHook(), ex));
    }

    private void runToggle(ButtonInteractionEvent event, VoiceChannel channel, Permission permission, String onKey, String offKey) {
        event.deferReply(true).queue();
        CompletableFuture.supplyAsync(() -> tempVoiceService.toggle(channel, permission))
                .thenAccept(on -> event.getHook().sendMessage(templateService.msg(event.getGuild(), on ? onKey : offKey)).queue())
                .exceptionally(ex -> fail(event.getHook(), ex));
    }

    private VoiceChannel ownedChannel(Object channel, Member member) {
        if (!(channel instanceof VoiceChannel vc) || member == null) return null;
        return tempVoiceService.isOwner(vc, member) ? vc : null;
    }

    private static Void fail(net.dv8tion.jda.api.interactions.InteractionHook hook, Throwable ex) {
        LOGGER.error("Erro no painel da sala temporária", ex);
        hook.sendMessage("❌ Não foi possível aplicar a mudança.").queue();
        return null;
    }
}
