package com.thalhuria.bot.service;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Placar do /top em imagem: cabeçalho, até 10 linhas com medalha, avatar, nome, detalhe, valor e uma barra
 * proporcional ao 1º colocado; opcionalmente a linha de quem pediu, destacada, no rodapé.
 */
public class LeaderboardRenderer {

    private static final int WIDTH = 1100;
    private static final int HEADER = 150;
    private static final int ROW = 80;
    private static final int GAP = 10;
    private static final int PAD = 40;

    private static final Color GOLD = new Color(0xF5C451);
    private static final Color SILVER = new Color(0xC7D2E0);
    private static final Color BRONZE = new Color(0xD08A5A);

    public record Row(int position, String name, String avatarUrl, String value, String detail, double ratio) {}

    public byte[] render(String label, String title, List<Row> rows, Row self, Color accent, Color background,
                         String backgroundImageUrl, long seed) throws Exception {
        int height = HEADER + rows.size() * (ROW + GAP) + (self != null ? 46 + ROW : 0) + 30;
        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);

        // Avatares em paralelo: 10 downloads em sequência deixariam o comando lento
        List<CompletableFuture<BufferedImage>> avatars = new ArrayList<>();
        for (Row row : rows) avatars.add(CompletableFuture.supplyAsync(() -> CardStyle.download(row.avatarUrl())));
        CompletableFuture<BufferedImage> selfAvatar = self != null
                ? CompletableFuture.supplyAsync(() -> CardStyle.download(self.avatarUrl()))
                : CompletableFuture.completedFuture(null);
        BufferedImage bgImage = CardStyle.download(backgroundImageUrl);

        Graphics2D g = CardStyle.setup(img);
        CardStyle.background(g, WIDTH, height, background, accent, bgImage, seed);

        // Cabeçalho
        Font labelFont = CardStyle.font(Font.BOLD, 22);
        CardStyle.spaced(g, CardStyle.printable(label, labelFont).toUpperCase(), PAD, 62, labelFont,
                CardStyle.mix(accent, Color.WHITE, 0.55f), 4f);
        Font titleFont = CardStyle.font(Font.BOLD, 52);
        String cleanTitle = CardStyle.printable(title, titleFont);
        titleFont = CardStyle.fit(g, cleanTitle, titleFont, WIDTH - PAD * 2, 30);
        CardStyle.goldText(g, CardStyle.ellipsize(g, cleanTitle, titleFont, WIDTH - PAD * 2), PAD, 118, titleFont, accent);
        // Divisória dourada com losango no centro
        g.setPaint(new GradientPaint(PAD, 0, CardStyle.withAlpha(accent, 0), WIDTH / 2f, 0, accent, true));
        g.fillRect(PAD, 134, WIDTH - PAD * 2, 2);
        g.setColor(accent);
        g.fill(new java.awt.geom.Path2D.Float() {{ moveTo(WIDTH / 2f, 128); lineTo(WIDTH / 2f + 7, 135); lineTo(WIDTH / 2f, 142); lineTo(WIDTH / 2f - 7, 135); closePath(); }});

        int y = HEADER;
        for (int i = 0; i < rows.size(); i++) {
            drawRow(g, rows.get(i), avatars.get(i).join(), y, accent, false);
            y += ROW + GAP;
        }

        if (self != null) {
            // Separador "· · ·" e a linha de quem pediu
            g.setColor(new Color(255, 255, 255, 90));
            for (int d = 0; d < 3; d++) g.fill(new Ellipse2D.Float(WIDTH / 2f - 20 + d * 18, y + 10, 6, 6));
            y += 36;
            drawRow(g, self, selfAvatar.join(), y, accent, true);
        }

        g.dispose();
        return CardStyle.png(img);
    }

    private void drawRow(Graphics2D g, Row row, BufferedImage avatar, int y, Color accent, boolean highlight) {
        Color medal = switch (row.position()) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> null;
        };

        // Fundo da linha
        int x = PAD;
        int w = WIDTH - PAD * 2;
        g.setColor(highlight ? CardStyle.withAlpha(new Color(0xB3141F), 150) : new Color(0, 0, 0, medal != null ? 90 : 60));
        g.fill(new RoundRectangle2D.Float(x, y, w, ROW, 22, 22));
        if (medal != null || highlight) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(highlight ? accent.brighter() : CardStyle.withAlpha(medal, 170));
            g.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 2, ROW - 2, 22, 22));
        }

        // Posição
        int cx = x + 46;
        int cy = y + ROW / 2;
        if (medal != null) {
            g.setPaint(new GradientPaint(cx - 22, cy - 22, medal.brighter(), cx + 22, cy + 22, medal.darker()));
            g.fill(new Ellipse2D.Float(cx - 22, cy - 22, 44, 44));
        }
        Font posFont = CardStyle.font(Font.BOLD, row.position() >= 100 ? 20 : 26);
        String pos = row.position() > 0 ? String.valueOf(row.position()) : "—";
        g.setFont(posFont);
        int pw = g.getFontMetrics().stringWidth(pos);
        CardStyle.text(g, pos, cx - pw / 2, cy + 9, posFont, medal != null ? new Color(30, 22, 10) : Color.WHITE);

        // Avatar
        int size = 56;
        int ax = x + 90;
        int ay = y + (ROW - size) / 2;
        Shape old = g.getClip();
        g.setClip(new Ellipse2D.Float(ax, ay, size, size));
        if (avatar != null) {
            g.drawImage(avatar, ax, ay, size, size, null);
        } else {
            g.setPaint(new GradientPaint(ax, ay, accent.darker(), ax + size, ay + size, accent));
            g.fillRect(ax, ay, size, size);
        }
        g.setClip(old);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(medal != null ? medal : CardStyle.withAlpha(accent, 200));
        g.draw(new Ellipse2D.Float(ax, ay, size, size));

        // Valor (direita)
        Font valueFont = CardStyle.font(Font.BOLD, 30);
        String value = CardStyle.printable(row.value(), valueFont);
        int vw = g.getFontMetrics(valueFont).stringWidth(value);
        int right = x + w - 26;
        CardStyle.text(g, value, right - vw, y + 46, valueFont, medal != null ? medal.brighter() : Color.WHITE);

        // Nome, detalhe e barra
        int tx = ax + size + 22;
        int textMax = right - vw - 30 - tx;
        Font nameFont = CardStyle.font(Font.BOLD, 26);
        String name = CardStyle.printable(row.name(), nameFont);
        name = CardStyle.ellipsize(g, name, nameFont, textMax);
        CardStyle.text(g, name, tx, y + 36, nameFont, Color.WHITE);

        int barW = 200;
        int barX = right - barW;
        CardStyle.bar(g, barX, y + 58, barW, 8, Math.max(0.02, Math.min(1, row.ratio())), medal != null ? medal : accent);

        Font detailFont = CardStyle.font(Font.PLAIN, 17);
        String detail = CardStyle.ellipsize(g, CardStyle.printable(row.detail(), detailFont), detailFont, barX - 24 - tx);
        CardStyle.text(g, detail, tx, y + 64, detailFont, new Color(255, 255, 255, 165));
    }
}
