package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de XP e níveis: XP por mensagem (com cooldown) e por minuto em call.
 */
public class LevelingConfig {
    private boolean enabled = true;
    private int xpMin = 15;
    private int xpMax = 25;
    private int cooldownSeconds = 60;
    private int xpPerVoiceMinute = 10;
    private String announceChannelKey;
    private String levelUpMessage = "🎉 {user} subiu para o **nível {level}**!";
    private boolean stackRewards = true;
    private List<LevelReward> rewards = new ArrayList<>();
    private List<String> ignoredChannelKeys = new ArrayList<>();

    public LevelingConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getXpMin() {
        return xpMin;
    }

    public void setXpMin(int xpMin) {
        this.xpMin = xpMin;
    }

    public int getXpMax() {
        return xpMax;
    }

    public void setXpMax(int xpMax) {
        this.xpMax = xpMax;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getXpPerVoiceMinute() {
        return xpPerVoiceMinute;
    }

    public void setXpPerVoiceMinute(int xpPerVoiceMinute) {
        this.xpPerVoiceMinute = xpPerVoiceMinute;
    }

    public String getAnnounceChannelKey() {
        return announceChannelKey;
    }

    public void setAnnounceChannelKey(String announceChannelKey) {
        this.announceChannelKey = announceChannelKey;
    }

    public String getLevelUpMessage() {
        return levelUpMessage;
    }

    public void setLevelUpMessage(String levelUpMessage) {
        this.levelUpMessage = levelUpMessage;
    }

    public boolean isStackRewards() {
        return stackRewards;
    }

    public void setStackRewards(boolean stackRewards) {
        this.stackRewards = stackRewards;
    }

    public List<LevelReward> getRewards() {
        return rewards;
    }

    public void setRewards(List<LevelReward> rewards) {
        this.rewards = rewards != null ? rewards : new ArrayList<>();
    }

    public List<String> getIgnoredChannelKeys() {
        return ignoredChannelKeys;
    }

    public void setIgnoredChannelKeys(List<String> ignoredChannelKeys) {
        this.ignoredChannelKeys = ignoredChannelKeys != null ? ignoredChannelKeys : new ArrayList<>();
    }
}
