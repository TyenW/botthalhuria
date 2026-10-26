package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Moderação: canal de logs de moderação, eventos registrados, escalonamento de advertências e AutoMod.
 */
public class ModerationConfig {
    private String modLogChannelKey;
    private boolean logMessageEdits = true;
    private boolean logMessageDeletes = true;
    private boolean logVoice = true;
    private int warningExpiryDays = 30;
    private List<WarnThreshold> warnThresholds = new ArrayList<>();
    private AutoModConfig automod = new AutoModConfig();

    public ModerationConfig() {}

    public String getModLogChannelKey() {
        return modLogChannelKey;
    }

    public void setModLogChannelKey(String modLogChannelKey) {
        this.modLogChannelKey = modLogChannelKey;
    }

    public boolean isLogMessageEdits() {
        return logMessageEdits;
    }

    public void setLogMessageEdits(boolean logMessageEdits) {
        this.logMessageEdits = logMessageEdits;
    }

    public boolean isLogMessageDeletes() {
        return logMessageDeletes;
    }

    public void setLogMessageDeletes(boolean logMessageDeletes) {
        this.logMessageDeletes = logMessageDeletes;
    }

    public boolean isLogVoice() {
        return logVoice;
    }

    public void setLogVoice(boolean logVoice) {
        this.logVoice = logVoice;
    }

    public int getWarningExpiryDays() {
        return warningExpiryDays;
    }

    public void setWarningExpiryDays(int warningExpiryDays) {
        this.warningExpiryDays = warningExpiryDays;
    }

    public List<WarnThreshold> getWarnThresholds() {
        return warnThresholds;
    }

    public void setWarnThresholds(List<WarnThreshold> warnThresholds) {
        this.warnThresholds = warnThresholds != null ? warnThresholds : new ArrayList<>();
    }

    public AutoModConfig getAutomod() {
        return automod;
    }

    public void setAutomod(AutoModConfig automod) {
        this.automod = automod != null ? automod : new AutoModConfig();
    }
}
