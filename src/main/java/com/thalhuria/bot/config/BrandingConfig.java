package com.thalhuria.bot.config;

/**
 * Identidade visual do servidor: aplicada em todos os embeds e painéis gerados pelo bot.
 */
public class BrandingConfig {
    private String name = "Thaluria";
    private String tagline = "";
    private String colorHex = Palette.GOLD_HEX;
    private String footer = "Thaluria";
    private String iconUrl;
    private String bannerUrl;
    private String activity = "Thaluria | /server-status";

    public BrandingConfig() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }
}
