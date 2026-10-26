package com.thalhuria.bot.config;

/**
 * Eventos agendados: anúncio e lembrete automático.
 */
public class EventsConfig {
    private boolean enabled = true;
    private String announceChannelKey;
    private String pingRoleKey;
    private int reminderMinutes = 15;
    private String timezone = "America/Sao_Paulo";

    public EventsConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAnnounceChannelKey() {
        return announceChannelKey;
    }

    public void setAnnounceChannelKey(String announceChannelKey) {
        this.announceChannelKey = announceChannelKey;
    }

    public String getPingRoleKey() {
        return pingRoleKey;
    }

    public void setPingRoleKey(String pingRoleKey) {
        this.pingRoleKey = pingRoleKey;
    }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
