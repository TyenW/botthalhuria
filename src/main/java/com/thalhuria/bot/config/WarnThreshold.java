package com.thalhuria.bot.config;

/**
 * Punição automática ao atingir uma quantidade de advertências ativas.
 */
public class WarnThreshold {
    private int warnings = 3;
    private String action = "TIMEOUT";
    private int durationMinutes = 60;

    public WarnThreshold() {}

    public int getWarnings() {
        return warnings;
    }

    public void setWarnings(int warnings) {
        this.warnings = warnings;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
