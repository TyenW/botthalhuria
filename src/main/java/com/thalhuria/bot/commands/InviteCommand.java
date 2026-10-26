package com.thalhuria.bot.commands;

import com.thalhuria.bot.config.InviteTier;
import com.thalhuria.bot.config.InvitesConfig;
import com.thalhuria.bot.database.InviteRepository.InviterStats;
import com.thalhuria.bot.database.InviteRepository.RankEntry;
import com.thalhuria.bot.service.EconomyService;
import com.thalhuria.bot.service.InviteService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * /convites ver [usuario] e /convites ranking.
 */
public class InviteCommand implements SlashCommandHandler {

    private final InviteService inviteService;
    private final EconomyService economyService;
    private final TemplateService templateService;

    public InviteCommand(InviteService inviteService, EconomyService economyService, TemplateService templateService) {
        this.inviteService = inviteService;
        this.economyService = economyService;
        this.templateService = templateService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("convites", "Quantas pessoas você trouxe para o servidor")
                .addSubcommands(
                        new SubcommandData("ver", "Mostra seus convites (ou de outro membro)")
                                .addOption(OptionType.USER, "usuario", "Ver os convites de outro membro", false),
                        new SubcommandData("ranking", "Os maiores recrutadores do servidor"))
                .setGuildOnly(true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null || event.getMember() == null) return;
        InvitesConfig config = inviteService.config(guild);
        if (!config.isEnabled()) {
            event.reply(templateService.msg(guild, "invites.disabled")).setEphemeral(true).queue();
            return;
        }
        if ("ranking".equals(event.getSubcommandName())) ranking(event, guild, config);
        else view(event, guild, config);
    }

    private void view(SlashCommandInteractionEvent event, Guild guild, InvitesConfig config) {
        Member target = event.getOption("usuario", event.getMember(), o -> o.getAsMember());
        if (target == null) target = event.getMember();
        InviterStats stats = inviteService.repository().stats(guild.getId(), target.getId(), config.isRewardOnlyVerified());

        List<InviteTier> tiers = config.getTiers().stream().sorted(Comparator.comparingInt(InviteTier::getInvites)).toList();
        Optional<InviteTier> next = tiers.stream().filter(t -> t.getInvites() > stats.counted()).findFirst();

        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setAuthor(target.getEffectiveName(), null, target.getUser().getEffectiveAvatarUrl())
                .setTitle(templateService.msg(guild, "invites.view.title"))
                .setThumbnail(target.getUser().getEffectiveAvatarUrl())
                .setDescription(templateService.msg(guild, "invites.view.description", "count", stats.counted()))
                .addField("✅ Válidos", String.valueOf(stats.counted()), true)
                .addField("⏳ Aguardando verificação", String.valueOf(stats.pending()), true)
                .addField("🚪 Saíram", String.valueOf(stats.left()), true)
                .addField("🚫 Contas novas", String.valueOf(stats.fake()), true)
                .addField("📨 Total de entradas", String.valueOf(stats.total()), true);
        if (economyService.config(guild).isEnabled() && config.getCoinsPerInvite() > 0) {
            embed.addField("🪙 Por convite", economyService.format(guild, config.getCoinsPerInvite()), true);
        }
        next.ifPresent(tier -> {
            String roleName = templateService.role(guild, tier.getRoleKey()).map(Role::getAsMention).orElse(tier.getRoleKey());
            int missing = tier.getInvites() - stats.counted();
            embed.addField(templateService.msg(guild, "invites.view.next.title"),
                    templateService.msg(guild, "invites.view.next", "role", roleName, "missing", missing, "total", tier.getInvites()), false);
        });
        embed.setFooter(templateService.msg(guild, "invites.view.footer", "days", config.getMinAccountAgeDays()));
        event.replyEmbeds(embed.build()).queue();
    }

    private void ranking(SlashCommandInteractionEvent event, Guild guild, InvitesConfig config) {
        List<RankEntry> top = inviteService.repository().top(guild.getId(), config.isRewardOnlyVerified(), 10);
        if (top.isEmpty()) {
            event.reply(templateService.msg(guild, "invites.ranking.empty")).setEphemeral(true).queue();
            return;
        }
        String[] medals = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            sb.append(i < 3 ? medals[i] : "`#" + (i + 1) + "`").append(" <@").append(top.get(i).inviterId()).append("> — **")
                    .append(top.get(i).counted()).append("** convite(s)\n");
        }
        event.replyEmbeds(templateService.brandedEmbed(guild)
                .setTitle(templateService.msg(guild, "invites.ranking.title"))
                .setDescription(sb.toString())
                .build()).queue();
    }
}
