package com.thalhuria.bot.commands;

import com.thalhuria.bot.database.MemberStatsRepository.MemberStats;
import com.thalhuria.bot.service.ActivityService;
import com.thalhuria.bot.service.EconomyService;
import com.thalhuria.bot.service.TemplateService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import com.thalhuria.bot.config.LevelReward;
import com.thalhuria.bot.config.Palette;
import com.thalhuria.bot.config.RoleTemplate;
import com.thalhuria.bot.config.ServerTemplate;
import com.thalhuria.bot.config.WelcomeCardConfig;
import com.thalhuria.bot.service.RankCardRenderer;
import com.thalhuria.bot.service.LeaderboardRenderer;
import com.thalhuria.bot.service.InviteService;
import com.thalhuria.bot.database.MemberStatsRepository;
import net.dv8tion.jda.api.entities.MessageEmbed;
import java.util.ArrayList;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /rank (cartão de progresso) e /top (ranking de XP ou de moedas).
 */
public class LevelCommands implements SlashCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LevelCommands.class);

    private final ActivityService activityService;
    private final EconomyService economyService;
    private final TemplateService templateService;
    private final InviteService inviteService;
    private final RankCardRenderer rankCardRenderer = new RankCardRenderer();
    private final LeaderboardRenderer leaderboardRenderer = new LeaderboardRenderer();

    public LevelCommands(ActivityService activityService, EconomyService economyService, TemplateService templateService,
                         InviteService inviteService) {
        this.activityService = activityService;
        this.economyService = economyService;
        this.templateService = templateService;
        this.inviteService = inviteService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("rank", "Mostra seu nível, XP e posição no ranking")
                .addOption(OptionType.USER, "usuario", "Ver o rank de outro membro", false)
                .setGuildOnly(true);
    }

    @Override
    public List<SlashCommandData> getAllCommandData() {
        return List.of(getCommandData(),
                Commands.slash("top", "Placar dos membros mais ativos do servidor")
                        .addOptions(new OptionData(OptionType.STRING, "ranking", "Qual placar mostrar (padrão: nível)", false)
                                .addChoice("⚔️ Nível (XP)", "xp")
                                .addChoice("🪙 Riqueza (Thalurians)", "moedas")
                                .addChoice("🔊 Tempo em call", "call")
                                .addChoice("💬 Mensagens", "mensagens")
                                .addChoice("🎟️ Convites", "convites"))
                        .setGuildOnly(true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;
        if (event.getName().equals("top")) {
            top(event, guild, event.getOption("ranking", "xp", o -> o.getAsString()));
        } else {
            rank(event, guild);
        }
    }

    private void rank(SlashCommandInteractionEvent event, Guild guild) {
        Member target = event.getOption("usuario", event.getMember(), o -> o.getAsMember());
        if (target == null || target.getUser().isBot()) {
            event.reply(templateService.msg(guild, "rank.invalid")).setEphemeral(true).queue();
            return;
        }
        MemberStats s = activityService.stats(target);
        long levelStart = ActivityService.totalXpForLevel(s.level());
        long needed = ActivityService.xpToNext(s.level());
        long progress = s.xp() - levelStart;
        int position = activityService.repository().rankOf(guild.getId(), s.xp());

        // Cartão em imagem (baixa o avatar): fora da thread de eventos, com o embed de texto como reserva
        event.deferReply().queue();
        CompletableFuture.supplyAsync(() -> {
            try {
                return rankCardRenderer.render(rankData(guild, target, s, position, progress, needed));
            } catch (Exception e) {
                LOGGER.warn("Falha ao gerar cartão de rank: {}", e.getMessage());
                return null;
            }
        }).thenAccept(png -> {
            if (png != null) {
                event.getHook().sendFiles(FileUpload.fromData(png, "rank.png")).queue();
            } else {
                event.getHook().sendMessageEmbeds(rankEmbed(guild, target, s, position, progress, needed)).queue();
            }
        });
    }

    private RankCardRenderer.RankData rankData(Guild guild, Member target, MemberStats s, int position, long progress, long needed) {
        ServerTemplate template = templateService.get(guild);
        List<LevelReward> rewards = template.getLeveling().getRewards().stream()
                .sorted(Comparator.comparingInt(LevelReward::getLevel)).toList();

        String title = null;
        String next = null;
        for (LevelReward reward : rewards) {
            String roleName = templateService.role(guild, reward.getRoleKey()).map(Role::getName)
                    .orElse(template.findRole(reward.getRoleKey()).map(RoleTemplate::getName).orElse(reward.getRoleKey()));
            if (reward.getLevel() <= s.level()) title = roleName;
            else if (next == null) next = roleName + " no nível " + reward.getLevel();
        }

        WelcomeCardConfig card = template.getWelcomeCard();
        Color accent = target.getColor() != null ? target.getColor() : templateService.brandColor(guild);
        boolean economy = economyService.config(guild).isEnabled();
        return new RankCardRenderer.RankData(
                target.getEffectiveName(), target.getUser().getEffectiveAvatarUrl(), title,
                s.level(), position, progress, needed, s.xp(), s.messages(), s.voiceMinutes(),
                economy ? s.coins() : null, economyService.config(guild).getCurrencyName(), next,
                accent, TemplateService.parseColor(card.getBackgroundColorHex(), Palette.CRIMSON),
                card.getBackgroundImageUrl(), target.getIdLong());
    }

    private net.dv8tion.jda.api.entities.MessageEmbed rankEmbed(Guild guild, Member target, MemberStats s, int position, long progress, long needed) {
        EmbedBuilder embed = templateService.brandedEmbed(guild)
                .setAuthor(target.getEffectiveName(), null, target.getUser().getEffectiveAvatarUrl())
                .setThumbnail(target.getUser().getEffectiveAvatarUrl())
                .setTitle("⚔️ Nível " + s.level())
                .setDescription(ActivityService.progressBar(progress, needed, 14) + "\n`" + progress + " / " + needed + " XP`")
                .addField("🏆 Posição", "#" + position, true)
                .addField("✨ XP total", String.format("%,d", s.xp()).replace(',', '.'), true)
                .addField("💬 Mensagens", String.format("%,d", s.messages()).replace(',', '.'), true)
                .addField("🔊 Em call", (s.voiceMinutes() / 60) + "h " + (s.voiceMinutes() % 60) + "min", true);
        if (economyService.config(guild).isEnabled()) {
            embed.addField("💰 Carteira", economyService.format(guild, s.coins()), true);
        }
        return embed.build();
    }

    /** Uma linha já calculada do placar, antes de virar imagem ou texto. */
    private record Entry(int position, String userId, long value, String valueText, String detail) {}

    private void top(SlashCommandInteractionEvent event, Guild guild, String type) {
        Member caller = event.getMember();
        event.deferReply().queue();
        CompletableFuture.runAsync(() -> {
            Board board = "convites".equals(type) ? inviteBoard(guild, caller) : statsBoard(guild, caller, type);
            if (board.entries().isEmpty()) {
                event.getHook().sendMessage(templateService.msg(guild, "top.empty")).queue();
                return;
            }
            byte[] png = null;
            try {
                png = renderBoard(guild, board);
            } catch (Exception e) {
                LOGGER.warn("Falha ao gerar placar: {}", e.getMessage());
            }
            if (png != null) {
                event.getHook().sendFiles(FileUpload.fromData(png, "top.png")).queue();
            } else {
                event.getHook().sendMessageEmbeds(boardEmbed(guild, board)).queue();
            }
        }).exceptionally(ex -> {
            LOGGER.error("Erro no /top", ex);
            event.getHook().sendMessage("❌ Não foi possível montar o placar.").queue();
            return null;
        });
    }

    private record Board(String label, String title, List<Entry> entries, Entry self, long best) {}

    private Board statsBoard(Guild guild, Member caller, String type) {
        MemberStatsRepository.Metric metric = switch (type) {
            case "moedas" -> MemberStatsRepository.Metric.COINS;
            case "call" -> MemberStatsRepository.Metric.VOICE;
            case "mensagens" -> MemberStatsRepository.Metric.MESSAGES;
            default -> MemberStatsRepository.Metric.XP;
        };
        String currency = economyService.config(guild).getCurrencyName();
        String label = switch (metric) {
            case COINS -> "Tesouro do reino";
            case VOICE -> "Vozes do reino";
            case MESSAGES -> "Arautos do reino";
            default -> "Hall da fama";
        };
        String title = switch (metric) {
            case COINS -> "Os mais ricos de " + guild.getName();
            case VOICE -> "Quem mais vive nas calls";
            case MESSAGES -> "Quem mais movimenta o chat";
            default -> "Os maiores de " + guild.getName();
        };

        // Busca a mais para pular quem já saiu do servidor
        List<Entry> entries = new ArrayList<>();
        for (MemberStats s : activityService.repository().top(guild.getId(), metric, 30)) {
            if (guild.getMemberById(s.userId()) == null) continue;
            entries.add(new Entry(entries.size() + 1, s.userId(), metric.valueOf(s), format(metric, s, currency), detail(metric, s, currency)));
            if (entries.size() == 10) break;
        }

        Entry self = null;
        if (caller != null && entries.stream().noneMatch(e -> e.userId().equals(caller.getId()))) {
            MemberStats mine = activityService.stats(caller);
            long value = metric.valueOf(mine);
            if (value > 0) {
                int pos = activityService.repository().positionOf(guild.getId(), metric, value);
                self = new Entry(pos, caller.getId(), value, format(metric, mine, currency), detail(metric, mine, currency));
            }
        }
        long best = entries.isEmpty() ? 1 : Math.max(1, entries.get(0).value());
        return new Board(label, title, entries, self, best);
    }

    private Board inviteBoard(Guild guild, Member caller) {
        boolean verified = inviteService.config(guild).isRewardOnlyVerified();
        List<Entry> entries = new ArrayList<>();
        for (var rank : inviteService.repository().top(guild.getId(), verified, 30)) {
            if (guild.getMemberById(rank.inviterId()) == null) continue;
            entries.add(new Entry(entries.size() + 1, rank.inviterId(), rank.counted(),
                    rank.counted() + (rank.counted() == 1 ? " recruta" : " recrutas"), "Recrutador do reino"));
            if (entries.size() == 10) break;
        }
        Entry self = null;
        if (caller != null && entries.stream().noneMatch(e -> e.userId().equals(caller.getId()))) {
            int mine = inviteService.repository().stats(guild.getId(), caller.getId(), verified).counted();
            if (mine > 0) self = new Entry(0, caller.getId(), mine, mine + (mine == 1 ? " recruta" : " recrutas"), "Sua contagem");
        }
        long best = entries.isEmpty() ? 1 : Math.max(1, entries.get(0).value());
        return new Board("Recrutamento", "Maiores recrutadores", entries, self, best);
    }

    private byte[] renderBoard(Guild guild, Board board) throws Exception {
        WelcomeCardConfig card = templateService.get(guild).getWelcomeCard();
        List<LeaderboardRenderer.Row> rows = board.entries().stream().map(e -> row(guild, e, board.best())).toList();
        LeaderboardRenderer.Row self = board.self() != null ? row(guild, board.self(), board.best()) : null;
        return leaderboardRenderer.render(board.label(), board.title(), rows, self,
                templateService.brandColor(guild), TemplateService.parseColor(card.getBackgroundColorHex(), Palette.CRIMSON),
                card.getBackgroundImageUrl(), guild.getIdLong());
    }

    private LeaderboardRenderer.Row row(Guild guild, Entry e, long best) {
        Member m = guild.getMemberById(e.userId());
        String name = m != null ? m.getEffectiveName() : "Membro";
        String avatar = m != null ? m.getUser().getEffectiveAvatarUrl() : null;
        return new LeaderboardRenderer.Row(e.position(), name, avatar, e.valueText(), e.detail(), (double) e.value() / best);
    }

    private MessageEmbed boardEmbed(Guild guild, Board board) {
        String[] medals = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder();
        for (Entry e : board.entries()) {
            int i = e.position() - 1;
            sb.append(i < 3 ? medals[i] : "`#" + e.position() + "`").append(" <@").append(e.userId()).append("> — **")
                    .append(e.valueText()).append("** · ").append(e.detail()).append('\n');
        }
        if (board.self() != null) {
            sb.append("\n**Você:** ").append(board.self().position() > 0 ? "#" + board.self().position() + " · " : "")
                    .append(board.self().valueText());
        }
        return templateService.brandedEmbed(guild).setTitle("🏆 " + board.title()).setDescription(sb.toString()).build();
    }

    private static String format(MemberStatsRepository.Metric metric, MemberStats s, String currency) {
        return switch (metric) {
            case COINS -> number(s.coins()) + " " + currency;
            case VOICE -> s.voiceMinutes() >= 60 ? number(s.voiceMinutes() / 60) + "h " + (s.voiceMinutes() % 60) + "min" : s.voiceMinutes() + " min";
            case MESSAGES -> number(s.messages()) + " msgs";
            default -> "Nível " + s.level();
        };
    }

    private static String detail(MemberStatsRepository.Metric metric, MemberStats s, String currency) {
        return switch (metric) {
            case XP -> number(s.xp()) + " XP · " + number(s.messages()) + " mensagens";
            case COINS, VOICE, MESSAGES -> "Nível " + s.level() + " · " + number(s.xp()) + " XP";
        };
    }

    private static String number(long n) {
        return String.format("%,d", n).replace(',', '.');
    }
}
