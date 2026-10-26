package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Tipo de ticket (denúncia, parceria...) com prefixo, staff e mensagem próprios.
 */
public class TicketType {
    private String key;
    private String label;
    private String emoji;
    private String description;
    private String channelPrefix;
    private List<String> staffRoleKeys = new ArrayList<>();
    private String openMessage;

    public TicketType() {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChannelPrefix() {
        return channelPrefix;
    }

    public void setChannelPrefix(String channelPrefix) {
        this.channelPrefix = channelPrefix;
    }

    public List<String> getStaffRoleKeys() {
        return staffRoleKeys;
    }

    public void setStaffRoleKeys(List<String> staffRoleKeys) {
        this.staffRoleKeys = staffRoleKeys != null ? staffRoleKeys : new ArrayList<>();
    }

    public String getOpenMessage() {
        return openMessage;
    }

    public void setOpenMessage(String openMessage) {
        this.openMessage = openMessage;
    }
}
