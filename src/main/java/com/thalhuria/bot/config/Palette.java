package com.thalhuria.bot.config;

import java.awt.Color;

/**
 * Paleta extraída da logo de Thaluria (carmesim, ouro envelhecido e couro escuro).
 * Usada como padrão nos cartões, embeds e botões; o template ainda pode sobrescrever as cores.
 */
public final class Palette {

    /** Carmesim profundo: fundo principal dos cartões. */
    public static final Color CRIMSON = new Color(0x4A0E0E);
    /** Couro/carvão: bordas, sombras e barras vazias. */
    public static final Color LEATHER = new Color(0x1E0707);
    /** Ouro Thaluria: cor dos embeds, títulos, barras de XP e molduras. */
    public static final Color GOLD = new Color(0xD4AF6A);
    /** Pergaminho claro: textos de destaque sobre o vermelho. */
    public static final Color PARCHMENT = new Color(0xEED49F);
    /** Rubi vivo: moderação, avisos e tickets fechados. */
    public static final Color RUBY = new Color(0x8B1E1E);
    /** Carmesim imperial: punições graves (banimento). */
    public static final Color IMPERIAL = new Color(0x990F0F);

    /** Medalhas e tiers: bronze → prata envelhecida → ouro. */
    public static final Color BRONZE = new Color(0x8C583A);
    public static final Color SILVER = new Color(0xA8A9AD);

    public static final String GOLD_HEX = "#D4AF6A";
    public static final String CRIMSON_HEX = "#4A0E0E";

    private Palette() {}
}
