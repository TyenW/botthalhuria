package com.thalhuria.bot.service;

import com.thalhuria.bot.config.EconomyConfig;
import com.thalhuria.bot.config.LevelReward;
import com.thalhuria.bot.config.LevelingConfig;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.database.MemberStatsRepository;
import com.thalhuria.bot.database.MemberStatsRepository.MemberStats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

/**
 * XP, níveis e moedas ganhos com atividade.
 *
 * Fórmula de níveis (a mesma popularizada pelo MEE6): subir do nível n para n+1 custa 5n² + 50n + 100 XP.
 * Todas as alterações passam por {@link #modify}, que é sincronizado: mensagens (thread de eventos do JDA)
 * e o tick de voz (thread do agendador) nunca sobrescrevem o progresso um do outro.
 */
public class ActivityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityService.class);

    private final MemberStatsRepository repository;
    private final TemplateService templateService;

    public ActivityService(MemberStatsRepository repository, TemplateService templateService) {
        this.repository = repository;
        this.templateService = templateService;
    }

    // ---------------------------------------------------------------- fórmula

    public static long xpToNext(int level) {
        return 5L * level * level + 50L * level + 100;
    }

    /** XP total acumulado necessário para chegar ao nível. */
    public static long totalXpForLevel(int level) {
        long total = 0;
        for (int i = 0; i < level; i++) total += xpToNext(i);
        return total;
    }

    public static int levelForXp(long xp) {
        int level = 0;
        long remaining = xp;
        while (remaining >= xpToNext(level)) {
            remaining -= xpToNext(level);
            level++;
        }
        return level;
    }

    // ---------------------------------------------------------------- acesso

    public MemberStats stats(Member member) {
        return repository.get(member.getGuild().getId(), member.getId());
    }

    public synchronized MemberStats modify(Guild guild, String userId, UnaryOperator<MemberStats> change) {
        MemberStats updated = change.apply(repository.get(guild.getId(), userId));
        repository.save(updated);
        return updated;
    }

    public MemberStatsRepository repository() {
        return repository;
    }

    // ---------------------------------------------------------------- ganho por atividade

    /**
     * Mensagem de um membro: conta a mensagem e, fora do cooldown, concede XP aleatório e moedas.
     */
    public void onMessage(Member member, GuildMessageChannel channel) {
        Guild guild = member.getGuild();
        ServerTemplate template = templateService.get(guild);
        LevelingConfig leveling = template.getLeveling();
        EconomyConfig economy = template.getEconomy();
        if (!leveling.isEnabled() && !economy.isEnabled()) return;
        if (isIgnored(guild, leveling, channel.getId())) return;

        long now = System.currentTimeMillis() / 1000;
        MemberStats before = stats(member);
        boolean cooldownOver = now - before.lastXpAt() >= leveling.getCooldownSeconds();

        int xpGain = leveling.isEnabled() && cooldownOver
                ? ThreadLocalRandom.current().nextInt(leveling.getXpMin(), leveling.getXpMax() + 1)
                : 0;
        int coinGain = economy.isEnabled() && cooldownOver ? economy.getCoinsPerMessage() : 0;

        MemberStats after = modify(guild, member.getId(), s -> new MemberStats(
                s.guildId(), s.userId(),
                s.xp() + xpGain, levelForXp(s.xp() + xpGain),
                s.messages() + 1, s.voiceMinutes(),
                s.coins() + coinGain,
                xpGain > 0 ? now : s.lastXpAt(),
                s.lastDailyAt(), s.dailyStreak()));

        if (after.level() > before.level()) {
            onLevelUp(member, before.level(), after.level(), channel);
        }
    }

    /**
     * Executado a cada minuto: quem está em call com pelo menos mais uma pessoa (sem estar ensurdecido) ganha XP e moedas.
     */
    public void voiceTick(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            try {
                tickGuild(guild);
            } catch (Exception e) {
                LOGGER.warn("Falha no tick de voz de {}: {}", guild.getName(), e.getMessage());
            }
        }
    }

    private void tickGuild(Guild guild) {
        ServerTemplate template = templateService.get(guild);
        LevelingConfig leveling = template.getLeveling();
        EconomyConfig economy = template.getEconomy();
        if (!leveling.isEnabled() && !economy.isEnabled()) return;

        VoiceChannel afk = guild.getAfkChannel();
        for (VoiceChannel vc : guild.getVoiceChannels()) {
            if (vc.equals(afk)) continue;
            List<Member> humans = vc.getMembers().stream().filter(m -> !m.getUser().isBot()).toList();
            if (humans.size() < 2) continue;

            for (Member member : humans) {
                GuildVoiceState state = member.getVoiceState();
                if (state == null || state.isDeafened()) continue;
                int xpGain = leveling.isEnabled() ? leveling.getXpPerVoiceMinute() : 0;
                int coinGain = economy.isEnabled() ? economy.getCoinsPerVoiceMinute() : 0;
                int oldLevel = stats(member).level();
                MemberStats after = modify(guild, member.getId(), s -> new MemberStats(
                        s.guildId(), s.userId(),
                        s.xp() + xpGain, levelForXp(s.xp() + xpGain),
                        s.messages(), s.voiceMinutes() + 1,
                        s.coins() + coinGain,
                        s.lastXpAt(), s.lastDailyAt(), s.dailyStreak()));
                if (after.level() > oldLevel) onLevelUp(member, oldLevel, after.level(), null);
            }
        }
    }

    /**
     * Soma (ou subtrai, com valores negativos) XP e moedas, recalculando o nível. Nunca deixa abaixo de zero.
     * Subiu de nível: anuncia normalmente (no canal de níveis ou em {@code announceIn}). Desceu: ajusta os títulos.
     */
    public MemberStats grant(Member member, long xp, long coins, GuildMessageChannel announceIn) {
        Guild guild = member.getGuild();
        int oldLevel = stats(member).level();
        MemberStats after = modify(guild, member.getId(), s -> {
            long newXp = Math.max(0, s.xp() + xp);
            return new MemberStats(s.guildId(), s.userId(), newXp, levelForXp(newXp), s.messages(), s.voiceMinutes(),
                    Math.max(0, s.coins() + coins), s.lastXpAt(), s.lastDailyAt(), s.dailyStreak());
        });
        if (after.level() > oldLevel) {
            onLevelUp(member, oldLevel, after.level(), announceIn);
        } else if (after.level() < oldLevel) {
            applyRewards(member, after.level());
        }
        return after;
    }

    // ---------------------------------------------------------------- níveis

    private void onLevelUp(Member member, int oldLevel, int newLevel, GuildMessageChannel origin) {
        Guild guild = member.getGuild();
        LevelingConfig leveling = templateService.get(guild).getLeveling();
        List<Role> earned = applyRewards(member, newLevel);

        Optional<GuildMessageChannel> target = templateService.messageChannel(guild, leveling.getAnnounceChannelKey())
                .or(() -> Optional.ofNullable(origin));
        target.filter(GuildMessageChannel::canTalk).ifPresent(channel -> {
            String text = templateService.render(guild, leveling.getLevelUpMessage(),
                    java.util.Map.of("user", member.getAsMention(), "username", member.getEffectiveName(), "level", String.valueOf(newLevel)));
            EmbedBuilder embed = templateService.brandedEmbed(guild)
                    .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                    .setTitle(templateService.msg(guild, earned.isEmpty() ? "level.up.title" : "level.up.title.reward", "level", newLevel))
                    .setDescription(text)
                    .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                    .addField(templateService.msg(guild, "level.up.progress"),
                            progressBar(0, xpToNext(newLevel), 12) + "\n`0 / " + xpToNext(newLevel) + " XP`", true);
            if (member.getColor() != null) embed.setColor(member.getColor());
            if (!earned.isEmpty()) {
                embed.addField(templateService.msg(guild, "level.up.reward.field"),
                        templateService.msg(guild, "level.reward", "roles",
                                String.join(" ", earned.stream().map(Role::getAsMention).toList())), true);
            }
            nextReward(guild, newLevel).ifPresent(next -> embed.setFooter(templateService.msg(guild, "level.up.next",
                    "role", next.name(), "level", next.level()), member.getUser().getEffectiveAvatarUrl()));
            // A menção fica no embed: não notifica o membro a cada nível
            channel.sendMessageEmbeds(embed.build()).queue(null, err -> { });
        });
        LOGGER.info("{} subiu do nível {} para {} em {}", member.getEffectiveName(), oldLevel, newLevel, guild.getName());
    }

    /**
     * Aplica os cargos de recompensa do nível atual. Com stackRewards=false, mantém só a recompensa mais alta.
     *
     * @return cargos recém-concedidos
     */
    public List<Role> applyRewards(Member member, int level) {
        Guild guild = member.getGuild();
        LevelingConfig leveling = templateService.get(guild).getLeveling();
        List<LevelReward> rewards = leveling.getRewards().stream()
                .sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .toList();

        List<Role> shouldHave = new ArrayList<>();
        List<Role> shouldNotHave = new ArrayList<>();
        Role highest = null;
        for (LevelReward reward : rewards) {
            Optional<Role> role = templateService.role(guild, reward.getRoleKey())
                    .filter(r -> guild.getSelfMember().canInteract(r));
            if (role.isEmpty()) continue;
            if (reward.getLevel() <= level) {
                shouldHave.add(role.get());
                highest = role.get();
            } else {
                shouldNotHave.add(role.get());
            }
        }
        if (!leveling.isStackRewards() && highest != null) {
            for (Role r : shouldHave) if (!r.equals(highest)) shouldNotHave.add(r);
            shouldHave = List.of(highest);
        }

        List<Role> add = shouldHave.stream().filter(r -> !member.getRoles().contains(r)).toList();
        List<Role> remove = shouldNotHave.stream().filter(member.getRoles()::contains).toList();
        if (!add.isEmpty() || !remove.isEmpty()) {
            guild.modifyMemberRoles(member, add, remove).reason("Recompensas de nível").queue(null,
                    err -> LOGGER.warn("Falha ao aplicar recompensas de nível em {}: {}", member.getEffectiveName(), err.getMessage()));
        }
        return add;
    }

    public record NextReward(String name, int level) {}

    /** Próximo cargo de recompensa acima do nível informado (nome sem emoji é responsabilidade de quem exibe). */
    public Optional<NextReward> nextReward(Guild guild, int level) {
        ServerTemplate template = templateService.get(guild);
        return template.getLeveling().getRewards().stream()
                .filter(r -> r.getLevel() > level)
                .min((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .map(r -> new NextReward(templateService.role(guild, r.getRoleKey()).map(Role::getName)
                        .orElse(template.findRole(r.getRoleKey()).map(com.thalhuria.bot.config.RoleTemplate::getName).orElse(r.getRoleKey())),
                        r.getLevel()));
    }

    private boolean isIgnored(Guild guild, LevelingConfig leveling, String channelId) {
        for (String key : leveling.getIgnoredChannelKeys()) {
            if (templateService.channel(guild, key).map(ch -> ch.getId().equals(channelId)).orElse(false)) return true;
        }
        return false;
    }

    /** Barra de progresso em texto: ▰▰▰▰▱▱▱▱▱▱ */
    public static String progressBar(long current, long total, int size) {
        int filled = total <= 0 ? 0 : (int) Math.min(size, Math.round((double) current / total * size));
        return "▰".repeat(filled) + "▱".repeat(size - filled);
    }
}
