package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de tickets: canais privados de atendimento abertos por botão.
 */
public class TicketConfig {
    private boolean enabled = true;
    private String categoryKey;
    private List<String> staffRoleKeys = new ArrayList<>();
    private String channelPrefix = "ticket-";
    private String openTitle = "🎫 Ticket aberto";
    private String openMessage = "Olá {user}! Descreva seu assunto com detalhes e a equipe responderá em breve.";
    private int closeDelaySeconds = 5;
    private boolean transcripts = true;
    private boolean dmTranscript = true;
    private List<TicketType> types = new ArrayList<>();

    public TicketConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey;
    }

    public List<String> getStaffRoleKeys() {
        return staffRoleKeys;
    }

    public void setStaffRoleKeys(List<String> staffRoleKeys) {
        this.staffRoleKeys = staffRoleKeys;
    }

    public String getChannelPrefix() {
        return channelPrefix;
    }

    public void setChannelPrefix(String channelPrefix) {
        this.channelPrefix = channelPrefix;
    }

    public String getOpenTitle() {
        return openTitle;
    }

    public void setOpenTitle(String openTitle) {
        this.openTitle = openTitle;
    }

    public String getOpenMessage() {
        return openMessage;
    }

    public void setOpenMessage(String openMessage) {
        this.openMessage = openMessage;
    }

    public int getCloseDelaySeconds() {
        return closeDelaySeconds;
    }

    public void setCloseDelaySeconds(int closeDelaySeconds) {
        this.closeDelaySeconds = closeDelaySeconds;
    }

    public boolean isTranscripts() {
        return transcripts;
    }

    public void setTranscripts(boolean transcripts) {
        this.transcripts = transcripts;
    }

    public boolean isDmTranscript() {
        return dmTranscript;
    }

    public void setDmTranscript(boolean dmTranscript) {
        this.dmTranscript = dmTranscript;
    }

    public List<TicketType> getTypes() {
        return types;
    }

    public void setTypes(List<TicketType> types) {
        this.types = types != null ? types : new ArrayList<>();
    }
}
