package com.thalhuria.bot.service;

import com.thalhuria.bot.config.WelcomeCardConfig;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Cartão de boas-vindas (PNG 1200x420):
 * avatar com glow à esquerda; à direita o título em letras espaçadas, o nome do membro em destaque,
 * um sublinhado em degradê, o selo "Habitante nº X" e a marca do servidor no rodapé.
 */
public class WelcomeCardRenderer {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 420;

    public byte[] render(WelcomeCardConfig config, Color brand, String avatarUrl, String username,
                         String title, String subtitle) throws Exception {
        return render(config, brand, avatarUrl, username, title, subtitle, null, username == null ? 0 : username.hashCode());
    }

    /**
     * @param footer texto pequeno no canto inferior direito (ex.: "THALHURIA • 1.234 HABITANTES"); null para omitir
     * @param seed   semente das partículas do fundo (use o ID do membro para cada cartão ser único)
     */
    public byte[] render(WelcomeCardConfig config, Color brand, String avatarUrl, String username,
                         String title, String subtitle, String footer, long seed) throws Exception {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = CardStyle.setup(img);

        Color base = TemplateService.parseColor(config.getBackgroundColorHex(), new Color(0x2A0A0F));
        Color accent = TemplateService.parseColor(config.getAccentColorHex(), brand);
        CardStyle.background(g, WIDTH, HEIGHT, base, accent, CardStyle.download(config.getBackgroundImageUrl()), seed);

        // Avatar
        int size = 250;
        int ax = 85;
        int ay = (HEIGHT - size) / 2;
        CardStyle.runeRing(g, ax + size / 2f, ay + size / 2f, size / 2f + 44, accent, seed);
        CardStyle.avatar(g, CardStyle.download(avatarUrl), ax, ay, size, accent, base.brighter());

        // Bloco de texto
        int tx = ax + size + 75;
        int maxWidth = WIDTH - tx - 70;

        Font titleFont = CardStyle.font(Font.BOLD, 26);
        String cleanTitle = CardStyle.printable(title, titleFont).toUpperCase();
        CardStyle.spaced(g, cleanTitle, tx, 135, titleFont, CardStyle.mix(accent, Color.WHITE, 0.55f), 4.5f);

        Font nameFont = CardStyle.font(Font.BOLD, 78);
        String name = CardStyle.printable(username, nameFont);
        nameFont = CardStyle.fit(g, name, nameFont, maxWidth);
        CardStyle.goldText(g, name, tx, 220, nameFont, accent);

        // Sublinhado em degradê com o tamanho do nome (mínimo 160px)
        int underline = Math.max(160, Math.min(maxWidth, g.getFontMetrics(nameFont).stringWidth(name)));
        g.setPaint(new GradientPaint(tx, 0, accent, tx + underline, 0, CardStyle.withAlpha(accent, 0)));
        g.fill(new RoundRectangle2D.Float(tx, 242, underline, 6, 6, 6));

        // Selo com o subtítulo
        Font pillFont = CardStyle.font(Font.BOLD, 28);
        String cleanSubtitle = CardStyle.printable(subtitle, pillFont);
        CardStyle.pill(g, cleanSubtitle, tx, 275, pillFont, CardStyle.withAlpha(new Color(0xB3141F), 200), CardStyle.mix(accent, Color.WHITE, 0.6f));

        if (footer != null && !footer.isBlank()) {
            Font footerFont = CardStyle.font(Font.BOLD, 18);
            String cleanFooter = CardStyle.printable(footer, footerFont).toUpperCase();
            int width = g.getFontMetrics(footerFont).stringWidth(cleanFooter) + cleanFooter.length() * 2;
            CardStyle.spaced(g, cleanFooter, WIDTH - width - 45, HEIGHT - 32, footerFont, new Color(255, 255, 255, 150), 2f);
        }

        g.dispose();
        return CardStyle.png(img);
    }
}
