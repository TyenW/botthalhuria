package com.thalhuria.bot.config;

/**
 * Cartão de boas-vindas em imagem (PNG gerado pelo bot).
 */
public class WelcomeCardConfig {
    private boolean enabled = true;
    private String backgroundColorHex = Palette.CRIMSON_HEX;
    private String accentColorHex;
    private String backgroundImageUrl;
    private String title = "BEM-VINDO(A)";
    private String subtitle = "Você é o membro nº {count}";

    public WelcomeCardConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackgroundColorHex() {
        return backgroundColorHex;
    }

    public void setBackgroundColorHex(String backgroundColorHex) {
        this.backgroundColorHex = backgroundColorHex;
    }

    public String getAccentColorHex() {
        return accentColorHex;
    }

    public void setAccentColorHex(String accentColorHex) {
        this.accentColorHex = accentColorHex;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
}
