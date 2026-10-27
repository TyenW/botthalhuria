package com.thalhuria.bot.service;

import com.thalhuria.bot.config.Palette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Random;

/**
 * Peças visuais compartilhadas pelos cartões (boas-vindas e rank): fundo carmesim com vinheta e partículas,
 * moldura dupla dourada, avatar com anel e glow, textos com sombra e letras espaçadas, barras e "pílulas".
 * Títulos usam Cinzel (serifada, como a logo); nomes, números e contadores usam Inter.
 */
final class CardStyle {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardStyle.class);
    private static final String[] FONT_PREFERENCE = {"Segoe UI", "Montserrat", "Poppins", "DejaVu Sans", "Arial", Font.SANS_SERIF};
    private static final String[] TITLE_FONT_PREFERENCE = {"Cinzel", "Georgia", "Times New Roman", "DejaVu Serif", Font.SERIF};
    private static final String FONT_FAMILY = pickFamily(FONT_PREFERENCE);
    private static final String TITLE_FAMILY = pickFamily(TITLE_FONT_PREFERENCE);
    // Fontes embutidas em /fonts: o cartão fica igual em qualquer máquina (inclusive no Docker, sem fontes do sistema)
    private static final Font INTER_REGULAR = loadFont("/fonts/Inter-Regular.ttf");
    private static final Font INTER_BOLD = loadFont("/fonts/Inter-Bold.ttf");
    private static final Font CINZEL_BOLD = loadFont("/fonts/Cinzel-Bold.ttf");

    private CardStyle() {}

    static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Fonte sem serifa para nomes, números e contadores. */
    static Font font(int style, float size) {
        Font embedded = (style & Font.BOLD) != 0 ? INTER_BOLD : INTER_REGULAR;
        if (embedded != null) return embedded.deriveFont((float) Math.round(size));
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    /** Fonte serifada medieval para títulos e rótulos ("BEM-VINDO", "RANK", "NÍVEL"). */
    static Font titleFont(float size) {
        if (CINZEL_BOLD != null) return CINZEL_BOLD.deriveFont((float) Math.round(size));
        return new Font(TITLE_FAMILY, Font.BOLD, Math.round(size));
    }

    /**
     * Fundo do cartão: imagem (com escurecimento em degradê) ou degradê radial da cor base (mais clara no centro,
     * quase preta nas bordas, como a vinheta da logo), com brasas e moldura dupla dourada.
     * {@code seed} deixa as partículas fixas por pessoa.
     */
    static void background(Graphics2D g, int w, int h, Color base, Color accent, BufferedImage image, long seed) {
        Shape card = new RoundRectangle2D.Float(0, 0, w, h, 44, 44);
        g.setClip(card);

        if (image != null) {
            double scale = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
            int iw = (int) (image.getWidth() * scale);
            int ih = (int) (image.getHeight() * scale);
            g.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
            g.setPaint(new GradientPaint(0, 0, withAlpha(Palette.LEATHER, 235), w, 0, withAlpha(Palette.LEATHER, 130)));
            g.fillRect(0, 0, w, h);
        } else {
            // Degradê radial: com a base padrão (#4A0E0E) vai de #5C1212 no centro a #1A0505 nas bordas
            Color center = scale(base, 1.24f);
            Color edge = scale(base, 0.35f);
            float radius = (float) Math.hypot(w / 2.0, h / 2.0);
            g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, radius,
                    new float[]{0f, 0.45f, 1f}, new Color[]{center, mix(center, edge, 0.4f), edge}));
            g.fillRect(0, 0, w, h);
            // Brilho quente sutil atrás do avatar e um toque da cor de destaque no centro
            glow(g, (int) (w * 0.18), (int) (h * 0.5), (int) (h * 0.85), withAlpha(scale(base, 1.6f), 70));
            glow(g, (int) (w * 0.55), (int) (h * 0.5), (int) (h * 0.9), withAlpha(accent, 14));
        }

        // Brasas douradas flutuando
        Random random = new Random(seed);
        Color ember = mix(accent, Color.WHITE, 0.25f);
        for (int i = 0; i < 80; i++) {
            int x = random.nextInt(w);
            int y = random.nextInt(h);
            float r = 0.8f + random.nextFloat() * 2.4f;
            g.setColor(withAlpha(i % 4 == 0 ? new Color(255, 120, 60) : ember, 30 + random.nextInt(110)));
            g.fill(new Ellipse2D.Float(x, y, r, r));
        }

        // Faixa de destaque na base
        g.setPaint(new GradientPaint(0, 0, withAlpha(accent, 0), w / 2f, 0, accent, true));
        g.fillRect(0, h - 6, w, 6);
        g.setClip(null);
        frame(g, w, h, accent);
    }

    /** Moldura dupla de grimório: borda externa e um segundo traço fino 6px para dentro. */
    static void frame(Graphics2D g, int w, int h, Color accent) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(withAlpha(accent, 190));
        g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 44, 44));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(withAlpha(accent, 140));
        g.draw(new RoundRectangle2D.Float(7, 7, w - 14, h - 14, 32, 32));
        g.setStroke(oldStroke);
    }

    /**
     * Círculo de runas (inspirado na logo): dois aros finos com marcas e pequenos símbolos entre eles.
     */
    static void runeRing(Graphics2D g, float cx, float cy, float radius, Color accent, long seed) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(withAlpha(accent, 120));
        g.draw(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
        float inner = radius - 16;
        g.setColor(withAlpha(accent, 80));
        g.draw(new Ellipse2D.Float(cx - inner, cy - inner, inner * 2, inner * 2));

        // Símbolos rúnicos simples: traços e ângulos desenhados entre os aros
        Random random = new Random(seed * 31 + 7);
        int count = 36;
        float mid = radius - 8;
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            float px = (float) (cx + Math.cos(angle) * mid);
            float py = (float) (cy + Math.sin(angle) * mid);
            var transform = g.getTransform();
            g.translate(px, py);
            g.rotate(angle + Math.PI / 2);
            g.setColor(withAlpha(accent, 110 + random.nextInt(90)));
            switch (random.nextInt(5)) {
                case 0 -> g.drawLine(0, -5, 0, 5);
                case 1 -> { g.drawLine(0, -5, 0, 5); g.drawLine(0, -1, 3, -4); }
                case 2 -> { g.drawLine(-3, 5, 0, -5); g.drawLine(0, -5, 3, 5); }
                case 3 -> { g.drawLine(0, -5, 0, 5); g.drawLine(0, 0, 3, 3); g.drawLine(0, 0, -3, 3); }
                default -> g.fill(new Ellipse2D.Float(-1.5f, -1.5f, 3, 3));
            }
            g.setTransform(transform);
        }
        g.setStroke(oldStroke);
    }

    /**
     * Avatar circular com glow, anel de destaque e anel interno escuro.
     */
    static void avatar(Graphics2D g, BufferedImage avatar, int x, int y, int size, Color accent, Color fallback) {
        for (int i = 6; i >= 1; i--) {
            int pad = i * 5;
            g.setColor(withAlpha(accent, 14));
            g.fill(new Ellipse2D.Float(x - pad, y - pad, size + pad * 2f, size + pad * 2f));
        }
        g.setPaint(new GradientPaint(x, y, accent.brighter(), x + size, y + size, accent.darker()));
        g.fill(new Ellipse2D.Float(x - 9, y - 9, size + 18, size + 18));
        g.setColor(Palette.LEATHER);
        g.fill(new Ellipse2D.Float(x - 3, y - 3, size + 6, size + 6));

        Shape old = g.getClip();
        g.setClip(new Ellipse2D.Float(x, y, size, size));
        if (avatar != null) {
            g.drawImage(avatar, x, y, size, size, null);
        } else {
            g.setPaint(new GradientPaint(x, y, fallback, x + size, y + size, accent));
            g.fillRect(x, y, size, size);
        }
        g.setClip(old);
    }

    /** Texto com sombra suave. */
    static void text(Graphics2D g, String text, int x, int y, Font font, Color color) {
        g.setFont(font);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, x + 2, y + 3);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    /** Texto dourado em degradê vertical (claro em cima, mais escuro embaixo), com sombra. */
    static void goldText(Graphics2D g, String text, int x, int y, Font font, Color accent) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(text, x + 2, y + 3);
        Paint old = g.getPaint();
        g.setPaint(new GradientPaint(0, y - fm.getAscent(), mix(accent, Color.WHITE, 0.55f), 0, y, mix(accent, new Color(0x7A4A12), 0.25f)));
        g.drawString(text, x, y);
        g.setPaint(old);
    }

    /** Texto em maiúsculas com espaçamento entre letras (títulos pequenos). */
    static int spaced(Graphics2D g, String text, int x, int y, Font font, Color color, float tracking) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (char c : text.toCharArray()) {
            String s = String.valueOf(c);
            g.setColor(new Color(0, 0, 0, 100));
            g.drawString(s, cx + 1, y + 2);
            g.setColor(color);
            g.drawString(s, cx, y);
            cx += fm.stringWidth(s) + Math.round(tracking);
        }
        return cx - x;
    }

    /** "Pílula" arredondada com texto; retorna a largura. */
    static int pill(Graphics2D g, String text, int x, int y, Font font, Color bg, Color fg) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int padX = 18;
        int width = fm.stringWidth(text) + padX * 2;
        int height = fm.getHeight() + 10;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
        g.setColor(fg);
        g.drawString(text, x + padX, y + (height + fm.getAscent() - fm.getDescent()) / 2);
        return width;
    }

    /** Barra de progresso arredondada com degradê. */
    static void bar(Graphics2D g, int x, int y, int w, int h, double ratio, Color accent) {
        g.setColor(withAlpha(Palette.LEATHER, 210));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        g.setColor(withAlpha(accent, 60));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, h, h));
        int filled = (int) Math.max(h, Math.min(w, w * ratio));
        g.setPaint(new GradientPaint(x, y, accent, x + filled, y, mix(accent, Color.WHITE, 0.45f)));
        g.fill(new RoundRectangle2D.Float(x, y, filled, h, h, h));
        g.setColor(new Color(255, 255, 255, 70));
        g.fill(new RoundRectangle2D.Float(x + 4, y + 3, Math.max(0, filled - 8), h / 3f, h / 3f, h / 3f));
    }

    /** Diminui a fonte até o texto caber. */
    static Font fit(Graphics2D g, String text, Font font, int maxWidth) {
        return fit(g, text, font, maxWidth, 14);
    }

    static Font fit(Graphics2D g, String text, Font font, int maxWidth, int minSize) {
        Font f = font;
        while (f.getSize() > minSize && g.getFontMetrics(f).stringWidth(text) > maxWidth) {
            f = f.deriveFont((float) f.getSize() - 2);
        }
        return f;
    }

    /** Corta o texto com "…" até caber na largura. */
    static String ellipsize(Graphics2D g, String text, Font font, int maxWidth) {
        FontMetrics fm = g.getFontMetrics(font);
        if (fm.stringWidth(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && fm.stringWidth(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + "…";
    }

    /**
     * Remove caracteres que a fonte não consegue desenhar (emojis viram quadradinhos em Java2D).
     */
    static String printable(String text, Font font) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (font.canDisplay(cp)) sb.appendCodePoint(cp);
        });
        String clean = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return clean.isEmpty() ? "?" : clean;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(
                clamp(a.getRed() + (b.getRed() - a.getRed()) * t),
                clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static BufferedImage download(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            // Avatares animados (.gif) e webp: pedir a versão PNG ao CDN do Discord
            String fixed = url.replace(".gif", ".png").replace(".webp", ".png");
            if (fixed.contains("cdn.discordapp.com") && !fixed.contains("size=")) {
                fixed += (fixed.contains("?") ? "&" : "?") + "size=512";
            }
            URLConnection conn = URI.create(fixed).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ThaluriaBot");
            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            LOGGER.debug("Não foi possível baixar imagem {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static void glow(Graphics2D g, int cx, int cy, int radius, Color color) {
        RadialGradientPaint paint = new RadialGradientPaint(cx, cy, radius,
                new float[]{0f, 1f}, new Color[]{color, withAlpha(color, 0)});
        g.setPaint(paint);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    /** Multiplica os canais RGB (clareia com fator > 1, escurece com < 1). */
    static Color scale(Color c, float factor) {
        return new Color(clamp(c.getRed() * factor), clamp(c.getGreen() * factor), clamp(c.getBlue() * factor));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static Font loadFont(String resource) {
        try (InputStream in = CardStyle.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            LOGGER.warn("Não foi possível carregar a fonte {}: {}", resource, e.getMessage());
            return null;
        }
    }

    private static String pickFamily(String[] preference) {
        try {
            var available = java.util.Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String name : preference) {
                if (available.contains(name)) return name;
            }
        } catch (Exception ignored) {
            // ambiente sem fontes: cai no padrão lógico
        }
        return Font.SANS_SERIF;
    }
}
