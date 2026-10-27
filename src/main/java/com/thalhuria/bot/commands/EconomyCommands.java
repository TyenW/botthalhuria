package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.EconomyConfig;
import com.thalhuria.bot.config.ShopItem;
import com.thalhuria.bot.listeners.PanelInteractionListener;
import com.thalhuria.bot.service.ActivityService;
import com.thalhuria.bot.service.EconomyService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /daily, /saldo, /pagar e /loja.
 */
public class EconomyCommands implements SlashCommandHandler {

    private final EconomyService economyService;
    private final ActivityService activityService;
    private final TemplateService templateService;

    public EconomyCommands(EconomyService economyService, ActivityService activityService, TemplateService templateService) {
        this.economyService = economyService;
        this.activityService = activityService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("daily", "Resgata sua recompensa diária").setGuildOnly(true);
    }

    @Override
    public List<SlashCommandData> getAllCommandData() {
        return List.of(getCommandData(),
                Commands.slash("saldo", "Mostra quantas moedas você (ou outro membro) tem")
                        .addOption(OptionType.USER, "usuario", "Ver o saldo de outro membro", false)
                        .setGuildOnly(true),
                Commands.slash("pagar", "Envia moedas para outro membro")
                        .addOption(OptionType.USER, "usuario", "Quem vai receber", true)
                        .addOptions(new OptionData(OptionType.INTEGER, "valor", "Quantidade de moedas", true).setMinValue(1))
                        .setGuildOnly(true),
                Commands.slash("loja", "Abre a loja de cargos do servidor").setGuildOnly(true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        switch (event.getName()) {
            case "daily" -> {
                var result = economyService.daily(member);
                event.replyEmbeds(result.embed()).setEphemeral(!result.claimed()).queue();
            }
            case "saldo" -> {
                Member target = event.getOption("usuario", member, o -> o.getAsMember());
                if (target == null) target = member;
                event.reply(templateService.msg(guild, "economy.balance", "user", target.getAsMention(),
                        "balance", economyService.format(guild, activityService.stats(target).coins()))).queue();
            }
            case "pagar" -> {
                Member target = event.getOption("usuario", null, o -> o.getAsMember());
                long amount = event.getOption("valor", 0L, o -> o.getAsLong());
                if (target == null) {
                    event.reply(templateService.msg(guild, "economy.pay.invalid")).setEphemeral(true).queue();
                    return;
                }
                event.deferReply().queue();
                Member to = target;
                CompletableFuture.supplyAsync(() -> economyService.pay(member, to, amount))
                        .thenAccept(msg -> event.getHook().sendMessage(msg).queue());
            }
            case "loja" -> shop(event, guild, member);
            default -> { }
        }
    }

    private void shop(SlashCommandInteractionEvent event, Guild guild, Member member) {
        EconomyConfig config = economyService.config(guild);
        if (!config.isEnabled() || config.getShop().isEmpty()) {
            event.reply(templateService.msg(guild, "shop.empty")).setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setTitle(templateService.msg(guild, "shop.title"))
                .setDescription(templateService.msg(guild, "shop.description",
                        "balance", economyService.format(guild, activityService.stats(member).coins())));

        List<SelectOption> options = new ArrayList<>();
        for (ShopItem item : config.getShop()) {
            boolean owned = templateService.role(guild, item.getRoleKey()).map(member.getRoles()::contains).orElse(false);
            embed.addField((item.getEmoji() != null ? item.getEmoji() + " " : "") + item.getName() + (owned ? " ✅" : ""),
                    (item.getDescription() != null ? item.getDescription() + "\n" : "") + economyService.format(guild, item.getPrice()), true);
            SelectOption option = SelectOption.of(truncate(item.getName(), 100), item.getKey())
                    .withDescription(truncate(item.getPrice() + " " + config.getCurrencyName() + (owned ? " · já é seu" : ""), 100));
            if (item.getEmoji() != null) option = option.withEmoji(Emoji.fromFormatted(item.getEmoji()));
            options.add(option);
        }
        event.replyEmbeds(embed.build())
                .addActionRow(StringSelectMenu.create(PanelInteractionListener.SHOP_MENU_ID)
                        .setPlaceholder(templateService.msg(guild, "shop.placeholder"))
                        .addOptions(options)
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
