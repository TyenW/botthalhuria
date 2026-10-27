package com.thalhuria.bot.service;

import com.thalhuria.bot.config.WelcomeCardConfig;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gera os cartões sem rede (avatar indisponível) e salva prévias em target/card-previews para conferência visual.
 */
class WelcomeCardRendererTest {

    private static final Path PREVIEWS = Path.of("target", "card-previews");

    @Test
    void rendersWelcomeCard() throws Exception {
        System.setProperty("java.awt.headless", "true");
        WelcomeCardConfig themed = new WelcomeCardConfig();
        themed.setBackgroundColorHex("#4A0E0E");
        themed.setAccentColorHex("#D4AF6A");
        byte[] png = new WelcomeCardRenderer().render(themed, new Color(0xD4AF6A),
                null, "Viajante 🐉 Lendário", "Bem-vindo(a) ao reino de Thaluria", "Habitante nº 1.234",
                "Thaluria • o reino da resenha", 42L);
        BufferedImage img = check(png, 1200, 420);
        save("welcome.png", png);
        assertNotNull(img);
    }

    @Test
    void rendersRankCard() throws Exception {
        System.setProperty("java.awt.headless", "true");
        RankCardRenderer.RankData data = new RankCardRenderer.RankData(
                "Cavaleira Aurora", null, "⚔️ Cavaleiro", 17, 3, 1240, 2395, 15420,
                4821, 1325, 3480L, "Thalurians", "🏰 Barão no nível 30",
                new Color(0xD4AF6A), new Color(0x4A0E0E), null, 7L);
        byte[] png = new RankCardRenderer().render(data);
        check(png, 1200, 380);
        save("rank.png", png);

        // Sem economia, sem título e com nome enorme: não pode quebrar o layout
        byte[] minimal = new RankCardRenderer().render(new RankCardRenderer.RankData(
                "Um Nome Absurdamente Grande Para Testar O Encaixe", null, null, 0, 120, 0, 100, 0,
                0, 0, null, null, null, new Color(0xD7263D), new Color(0x4A0E0E), null, 1L));
        check(minimal, 1200, 380);
        save("rank-minimal.png", minimal);
    }

    @Test
    void rendersLeaderboard() throws Exception {
        System.setProperty("java.awt.headless", "true");
        String[] names = {"Cavaleira Aurora", "Draco 🐉", "Lua", "Um Nome Gigantesco Que Não Cabe De Jeito Nenhum", "Ravi",
                "Bia", "Thor", "Mel", "Zé", "Kai"};
        java.util.List<LeaderboardRenderer.Row> rows = new java.util.ArrayList<>();
        long[] xp = {268400, 151200, 98700, 67600, 41000, 22000, 15420, 9800, 4100, 1200};
        for (int i = 0; i < names.length; i++) {
            rows.add(new LeaderboardRenderer.Row(i + 1, names[i], null, "Nível " + (50 - i * 5),
                    String.format("%,d XP · %,d mensagens", xp[i], xp[i] / 20).replace(',', '.'), (double) xp[i] / xp[0]));
        }
        LeaderboardRenderer.Row self = new LeaderboardRenderer.Row(37, "Você Mesmo", null, "Nível 6", "1.700 XP · 85 mensagens", 1700.0 / xp[0]);
        byte[] png = new LeaderboardRenderer().render("Hall da fama", "Os maiores de Thaluria", rows, self,
                new Color(0xD4AF6A), new Color(0x4A0E0E), null, 3L);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
        save("top.png", png);
    }

    private static BufferedImage check(byte[] png, int w, int h) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img);
        assertEquals(w, img.getWidth());
        assertEquals(h, img.getHeight());
        return img;
    }

    private static void save(String name, byte[] png) throws Exception {
        Files.createDirectories(PREVIEWS);
        Files.write(PREVIEWS.resolve(name), png);
    }
}
