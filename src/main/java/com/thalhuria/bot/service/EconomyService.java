package com.thalhuria.bot.service;

import com.thalhuria.bot.config.EconomyConfig;
import com.thalhuria.bot.config.ShopItem;
import com.thalhuria.bot.database.MemberStatsRepository.MemberStats;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Moeda do servidor: recompensa diária com sequência, transferências e loja de cargos.
 * Itens da loja com o mesmo "group" são exclusivos (ex.: só uma cor de nome por vez).
 */
public class EconomyService {

    private static final long DAILY_COOLDOWN = 20 * 3600L;   // pode resgatar de novo após 20h
    private static final long STREAK_WINDOW = 48 * 3600L;    // perde a sequência se ficar 48h sem resgatar

    private final ActivityService activityService;
    private final TemplateService templateService;
    private final AuditLogService auditLogService;

    public EconomyService(ActivityService activityService, TemplateService templateService, AuditLogService auditLogService) {
        this.activityService = activityService;
        this.templateService = templateService;
        this.auditLogService = auditLogService;
    }

    public EconomyConfig config(Guild guild) {
        return templateService.get(guild).getEconomy();
    }

    public String format(Guild guild, long amount) {
        EconomyConfig c = config(guild);
        return c.getCurrencyEmoji() + " **" + String.format("%,d", amount).replace(',', '.') + "** " + c.getCurrencyName();
    }

    /**
     * Resultado do /daily já pronto para exibir.
     */
    public record DailyResult(MessageEmbed embed, boolean claimed) {}

    /**
     * Tesouro diário: moedas + XP, bônus por sequência, bônus no 7º dia e chance de "baú dourado".
     * Bloqueante e sincronizado: dois /daily simultâneos nunca pagam duas vezes.
     */
    public DailyResult daily(Member member) {
        Guild guild = member.getGuild();
        EconomyConfig c = config(guild);
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl());
        if (!c.isEnabled()) {
            return new DailyResult(embed.setDescription(templateService.msg(guild, "economy.disabled")).build(), false);
        }

        synchronized (activityService) {
            long now = System.currentTimeMillis() / 1000;
            MemberStats current = activityService.stats(member);
            long elapsed = now - current.lastDailyAt();

            if (current.lastDailyAt() > 0 && elapsed < DAILY_COOLDOWN) {
                long next = current.lastDailyAt() + DAILY_COOLDOWN;
                embed.setTitle(templateService.msg(guild, "economy.daily.wait.title"))
                        .setDescription(templateService.msg(guild, "economy.daily.wait.description", "time", "<t:" + next + ":R>")
                                + "\n" + streakTrack(current.dailyStreak()));
                return new DailyResult(embed.build(), false);
            }

            boolean broken = current.lastDailyAt() > 0 && elapsed > STREAK_WINDOW && current.dailyStreak() > 1;
            int streak = current.lastDailyAt() > 0 && elapsed <= STREAK_WINDOW ? current.dailyStreak() + 1 : 1;

            long base = c.getDailyAmount();
            long streakBonus = (long) c.getDailyStreakBonus() * Math.min(streak - 1, c.getMaxStreakBonusDays());
            long weekly = streak % 7 == 0 ? c.getDailyWeeklyBonus() : 0;
            boolean lucky = ThreadLocalRandom.current().nextInt(100) < c.getDailyLuckyChance();
            long subtotal = base + streakBonus + weekly;
            long luckyBonus = lucky ? subtotal * (Math.max(1, c.getDailyLuckyMultiplier()) - 1) : 0;
            long total = subtotal + luckyBonus;
            long xp = c.getDailyXp() * (lucky ? Math.max(1, c.getDailyLuckyMultiplier()) : 1L);

            int newStreak = streak;
            activityService.modify(guild, member.getId(), s -> new MemberStats(s.guildId(), s.userId(), s.xp(), s.level(),
                    s.messages(), s.voiceMinutes(), s.coins(), s.lastXpAt(), now, newStreak));
            MemberStats after = activityService.grant(member, xp, total, null);

            // Ganho em uma linha, bônus em letra miúda, trilha da semana e saldo
            String gain = templateService.msg(guild, "economy.daily.gain", "amount", format(guild, total))
                    + (xp > 0 ? " · " + templateService.msg(guild, "economy.daily.xp", "xp", xp) : "");
            List<String> bonuses = new ArrayList<>();
            if (streakBonus > 0) bonuses.add(templateService.msg(guild, "economy.daily.bonus.streak", "amount", format(guild, streakBonus)));
            if (weekly > 0) bonuses.add(templateService.msg(guild, "economy.daily.bonus.weekly", "amount", format(guild, weekly)));
            if (lucky) bonuses.add(templateService.msg(guild, "economy.daily.bonus.lucky", "multiplier", c.getDailyLuckyMultiplier()));

            StringBuilder text = new StringBuilder();
            if (broken) text.append(templateService.msg(guild, "economy.daily.broken", "streak", current.dailyStreak())).append('\n');
            text.append(gain).append('\n');
            if (!bonuses.isEmpty()) text.append("-# ").append(String.join(" · ", bonuses)).append('\n');
            text.append(streakTrack(streak)).append('\n')
                    .append(templateService.msg(guild, "economy.daily.status",
                            "balance", format(guild, after.coins()), "time", "<t:" + (now + DAILY_COOLDOWN) + ":R>"));

            embed.setTitle(templateService.msg(guild, lucky ? "economy.daily.title.lucky" : "economy.daily.title"))
                    .setDescription(text);
            return new DailyResult(embed.build(), true);
        }
    }

    /** "🔥🔥🔥⬛⬛⬛🎁 3 dias" — a trilha recomeça a cada semana completa. */
    static String streakTrack(int streak) {
        int inWeek = streak <= 0 ? 0 : ((streak - 1) % 7) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 7; i++) sb.append(i <= inWeek ? (i == 7 ? "👑" : "🔥") : (i == 7 ? "🎁" : "⬛"));
        return sb + " **" + streak + "** " + (streak == 1 ? "dia" : "dias");
    }

    /**
     * Transfere moedas. Retorna a mensagem de resultado.
     */
    public String pay(Member from, Member to, long amount) {
        Guild guild = from.getGuild();
        if (!config(guild).isEnabled()) return templateService.msg(guild, "economy.disabled");
        if (amount <= 0) return templateService.msg(guild, "economy.pay.invalid");
        if (to.getUser().isBot() || to.equals(from)) return templateService.msg(guild, "economy.pay.invalid");

        synchronized (activityService) {
            MemberStats sender = activityService.stats(from);
            if (sender.coins() < amount) {
                return templateService.msg(guild, "economy.insufficient", "balance", format(guild, sender.coins()));
            }
            activityService.modify(guild, from.getId(), s -> withCoins(s, s.coins() - amount));
            activityService.modify(guild, to.getId(), s -> withCoins(s, s.coins() + amount));
        }
        return templateService.msg(guild, "economy.pay.ok", "amount", format(guild, amount), "user", to.getAsMention());
    }

    /**
     * Compra um item da loja: debita as moedas e concede o cargo (removendo os outros do mesmo grupo).
     */
    public String buy(Member member, String itemKey) {
        Guild guild = member.getGuild();
        EconomyConfig c = config(guild);
        if (!c.isEnabled()) return templateService.msg(guild, "economy.disabled");

        Optional<ShopItem> item = c.getShop().stream().filter(i -> i.getKey().equalsIgnoreCase(itemKey)).findFirst();
        if (item.isEmpty()) return templateService.msg(guild, "shop.unknown");
        Optional<Role> role = templateService.role(guild, item.get().getRoleKey());
        if (role.isEmpty() || !guild.getSelfMember().canInteract(role.get())) return templateService.msg(guild, "shop.unavailable");
        if (member.getRoles().contains(role.get())) return templateService.msg(guild, "shop.owned", "item", item.get().getName());

        AtomicReference<String> failure = new AtomicReference<>();
        synchronized (activityService) {
            MemberStats current = activityService.stats(member);
            if (current.coins() < item.get().getPrice()) {
                failure.set(templateService.msg(guild, "economy.insufficient", "balance", format(guild, current.coins())));
            } else {
                activityService.modify(guild, member.getId(), s -> withCoins(s, s.coins() - item.get().getPrice()));
            }
        }
        if (failure.get() != null) return failure.get();

        List<Role> remove = new ArrayList<>();
        if (item.get().getGroup() != null) {
            for (ShopItem other : c.getShop()) {
                if (other != item.get() && item.get().getGroup().equalsIgnoreCase(other.getGroup())) {
                    templateService.role(guild, other.getRoleKey()).filter(member.getRoles()::contains).ifPresent(remove::add);
                }
            }
        }
        guild.modifyMemberRoles(member, List.of(role.get()), remove).reason("Compra na loja").complete();
        auditLogService.logEvent(guild, "🛒 Compra na Loja",
                member.getAsMention() + " comprou **" + item.get().getName() + "** por " + format(guild, item.get().getPrice()),
                AuditLogService.COLOR_PURPLE);
        return templateService.msg(guild, "shop.bought", "item", item.get().getName(), "role", role.get().getAsMention());
    }

    private static MemberStats withCoins(MemberStats s, long coins) {
        return new MemberStats(s.guildId(), s.userId(), s.xp(), s.level(), s.messages(), s.voiceMinutes(),
                coins, s.lastXpAt(), s.lastDailyAt(), s.dailyStreak());
    }
}
