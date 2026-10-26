package com.thalhuria.bot.config;

/**
 * Botão de um painel. Ações: TICKET, VERIFY, ROLE (alterna um cargo) ou URL (link).
 */
public class PanelButton {
    private String label;
    private String emoji;
    private String style = "PRIMARY";
    private String action = "TICKET";
    private String ticketType;
    private String roleKey;
    private String url;

    public PanelButton() {}

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
