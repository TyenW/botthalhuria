package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Regras do AutoMod nativo do Discord criadas pelo bot (funcionam mesmo com o bot offline).
 */
public class AutoModConfig {
    private boolean enabled = true;
    private List<String> blockedWords = new ArrayList<>();
    private boolean blockInvites = true;
    private int mentionLimit = 5;
    private boolean antiSpam = true;
    private int timeoutMinutes = 0;
    private String blockMessage = "🚫 Mensagem bloqueada pelo AutoMod.";
    private List<String> exemptRoleKeys = new ArrayList<>();

    public AutoModConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBlockedWords() {
        return blockedWords;
    }

    public void setBlockedWords(List<String> blockedWords) {
        this.blockedWords = blockedWords != null ? blockedWords : new ArrayList<>();
    }

    public boolean isBlockInvites() {
        return blockInvites;
    }

    public void setBlockInvites(boolean blockInvites) {
        this.blockInvites = blockInvites;
    }

    public int getMentionLimit() {
        return mentionLimit;
    }

    public void setMentionLimit(int mentionLimit) {
        this.mentionLimit = mentionLimit;
    }

    public boolean isAntiSpam() {
        return antiSpam;
    }

    public void setAntiSpam(boolean antiSpam) {
        this.antiSpam = antiSpam;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public String getBlockMessage() {
        return blockMessage;
    }

    public void setBlockMessage(String blockMessage) {
        this.blockMessage = blockMessage;
    }

    public List<String> getExemptRoleKeys() {
        return exemptRoleKeys;
    }

    public void setExemptRoleKeys(List<String> exemptRoleKeys) {
        this.exemptRoleKeys = exemptRoleKeys != null ? exemptRoleKeys : new ArrayList<>();
    }
}
