package com.thalhuria.bot.service;

import com.thalhuria.bot.config.Palette;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Cartão do /rank (PNG 1200x380): avatar com anel dourado e runas, nome, título do cargo de nível,
 * posição e nível em destaque, barra de progresso, estatísticas e a próxima recompensa.
 */
public class RankCardRenderer {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 380;
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));

    public record RankData(
            String username,
            String avatarUrl,
            String titleRole,      // cargo de nível atual (ex.: "⚔️ Cavaleiro"), pode ser null
            int level,
            int position,
            long xpInLevel,
            long xpNeeded,
            long totalXp,
            long messages,
            long voiceMinutes,
            Long coins,            // null quando a economia está desligada
            String currencyName,
            String nextReward,     // ex.: "Barão no nível 30", pode ser null
            Color accent,
            Color background,
            String backgroundImageUrl,
            long seed
    ) {}

    public byte[] render(RankData d) throws Exception {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = CardStyle.setup(img);
        Color accent = d.accent();
        CardStyle.background(g, WIDTH, HEIGHT, d.background(), accent, CardStyle.download(d.backgroundImageUrl()), d.seed());

        // Avatar
        int size = 210;
        int ax = 70;
        int ay = 60;
        CardStyle.runeRing(g, ax + size / 2f, ay + size / 2f, size / 2f + 38, Palette.GOLD, d.seed());
        CardStyle.avatar(g, CardStyle.download(d.avatarUrl()), ax, ay, size, Palette.GOLD, d.background().brighter());

        int tx = ax + size + 60;
        int right = WIDTH - 60;

        // Posição e nível (canto superior direito)
        Font labelFont = CardStyle.titleFont(22);
        Font bigFont = CardStyle.font(Font.BOLD, 64);
        String levelValue = String.valueOf(d.level());
        String rankValue = "#" + d.position();
        int levelWidth = g.getFontMetrics(bigFont).stringWidth(levelValue);
        int rankWidth = g.getFontMetrics(bigFont).stringWidth(rankValue);
        int levelX = right - levelWidth;
        CardStyle.goldText(g, levelValue, levelX, 120, bigFont, Palette.PARCHMENT);
        CardStyle.spaced(g, "NÍVEL", levelX - 105, 118, labelFont, Palette.GOLD, 2f);
        int rankX = levelX - 105 - 40 - rankWidth;
        CardStyle.goldText(g, rankValue, rankX, 120, bigFont, Palette.GOLD);
        CardStyle.spaced(g, "RANK", rankX - 95, 118, labelFont, Palette.GOLD, 2f);

        // Nome e título
        int nameMax = rankX - 95 - tx - 30;
        Font nameFont = CardStyle.font(Font.BOLD, 56);
        String name = CardStyle.printable(d.username(), nameFont);
        nameMax = Math.max(200, nameMax);
        nameFont = CardStyle.fit(g, name, nameFont, nameMax, 34);
        name = CardStyle.ellipsize(g, name, nameFont, nameMax);
        CardStyle.goldText(g, name, tx, 118, nameFont, Palette.PARCHMENT);

        Font titleFont = CardStyle.font(Font.BOLD, 22);
        if (d.titleRole() != null) {
            String title = CardStyle.printable(d.titleRole(), titleFont);
            CardStyle.pill(g, title, tx, 134, titleFont, CardStyle.withAlpha(Palette.RUBY, 220), Palette.PARCHMENT);
        }

        // Barra de progresso
        int barY = 228;
        int barWidth = right - tx;
        Font smallFont = CardStyle.font(Font.BOLD, 22);
        String progress = NUMBER.format(d.xpInLevel()) + " / " + NUMBER.format(d.xpNeeded()) + " XP";
        String percent = Math.round(d.xpNeeded() == 0 ? 0 : (double) d.xpInLevel() / d.xpNeeded() * 100) + "%";
        CardStyle.text(g, progress, right - g.getFontMetrics(smallFont).stringWidth(progress), barY - 14, smallFont, CardStyle.withAlpha(Palette.PARCHMENT, 220));
        CardStyle.text(g, percent, tx, barY - 14, smallFont, Palette.PARCHMENT);
        CardStyle.bar(g, tx, barY, barWidth, 34, d.xpNeeded() == 0 ? 0 : (double) d.xpInLevel() / d.xpNeeded(), accent);

        // Estatísticas em cartõezinhos
        String[][] stats = d.coins() != null
                ? new String[][]{
                    {"XP TOTAL", NUMBER.format(d.totalXp())},
                    {"MENSAGENS", NUMBER.format(d.messages())},
                    {"EM CALL", formatHours(d.voiceMinutes())},
                    {CardStyle.printable(d.currencyName(), smallFont).toUpperCase(), NUMBER.format(d.coins())}}
                : new String[][]{
                    {"XP TOTAL", NUMBER.format(d.totalXp())},
                    {"MENSAGENS", NUMBER.format(d.messages())},
                    {"EM CALL", formatHours(d.voiceMinutes())}};
        int gap = 16;
        int boxWidth = (barWidth - gap * (stats.length - 1)) / stats.length;
        int boxY = 282;
        Font statLabel = CardStyle.titleFont(15);
        Font statValue = CardStyle.font(Font.BOLD, 28);
        for (int i = 0; i < stats.length; i++) {
            int bx = tx + i * (boxWidth + gap);
            g.setColor(CardStyle.withAlpha(Palette.LEATHER, 150));
            g.fill(new RoundRectangle2D.Float(bx, boxY, boxWidth, 68, 18, 18));
            g.setColor(CardStyle.withAlpha(Palette.GOLD, 90));
            g.draw(new RoundRectangle2D.Float(bx, boxY, boxWidth, 68, 18, 18));
            CardStyle.spaced(g, stats[i][0], bx + 18, boxY + 25, statLabel, CardStyle.withAlpha(Palette.GOLD, 200), 1.5f);
            CardStyle.text(g, stats[i][1], bx + 18, boxY + 57, CardStyle.fit(g, stats[i][1], statValue, boxWidth - 30), Color.WHITE);
        }

        // Próxima recompensa, abaixo do avatar
        if (d.nextReward() != null) {
            Font rewardFont = CardStyle.titleFont(17);
            String text = CardStyle.printable("PRÓXIMO: " + d.nextReward(), rewardFont).toUpperCase();
            Font fitted = CardStyle.fit(g, text, rewardFont, size + 40);
            int w = g.getFontMetrics(fitted).stringWidth(text);
            CardStyle.text(g, text, ax + (size - w) / 2, HEIGHT - 32, fitted, Palette.PARCHMENT);
        }

        g.dispose();
        return CardStyle.png(img);
    }

    private static String formatHours(long minutes) {
        return minutes >= 60 ? NUMBER.format(minutes / 60) + "h" : minutes + "min";
    }
}
