package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

public class ChannelTemplate {
    private String key;
    private String name;
    private String type = "TEXT"; // TEXT, VOICE, STAGE, ANNOUNCEMENT
    private String topic = "";
    private boolean isSystemLogChannel = false;
    private List<PermissionRule> permissionOverrides = new ArrayList<>();

    // Ajustes do canal
    private int slowmode = 0;
    private boolean nsfw = false;
    private int userLimit = 0;

    // Recursos automáticos
    private boolean joinToCreate = false;
    private String tempChannelName = "🔊・Sala de {user}";
    private int tempUserLimit = 0;
    private List<String> autoReactions = new ArrayList<>();
    private boolean autoThread = false;
    private PanelTemplate panel;
    private String counter; // MEMBERS, HUMANS, BOTS, ONLINE, BOOSTS — o nome do canal usa {value}

    public ChannelTemplate() {}

    public ChannelTemplate(String key, String name, String type, String topic) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.topic = topic;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public boolean isSystemLogChannel() {
        return isSystemLogChannel;
    }

    public void setSystemLogChannel(boolean systemLogChannel) {
        isSystemLogChannel = systemLogChannel;
    }

    public List<PermissionRule> getPermissionOverrides() {
        return permissionOverrides;
    }

    public void setPermissionOverrides(List<PermissionRule> permissionOverrides) {
        this.permissionOverrides = permissionOverrides;
    }

    public int getSlowmode() {
        return slowmode;
    }

    public void setSlowmode(int slowmode) {
        this.slowmode = slowmode;
    }

    public boolean isNsfw() {
        return nsfw;
    }

    public void setNsfw(boolean nsfw) {
        this.nsfw = nsfw;
    }

    public int getUserLimit() {
        return userLimit;
    }

    public void setUserLimit(int userLimit) {
        this.userLimit = userLimit;
    }

    public boolean isJoinToCreate() {
        return joinToCreate;
    }

    public void setJoinToCreate(boolean joinToCreate) {
        this.joinToCreate = joinToCreate;
    }

    public String getTempChannelName() {
        return tempChannelName;
    }

    public void setTempChannelName(String tempChannelName) {
        this.tempChannelName = tempChannelName;
    }

    public int getTempUserLimit() {
        return tempUserLimit;
    }

    public void setTempUserLimit(int tempUserLimit) {
        this.tempUserLimit = tempUserLimit;
    }

    public List<String> getAutoReactions() {
        return autoReactions;
    }

    public void setAutoReactions(List<String> autoReactions) {
        this.autoReactions = autoReactions;
    }

    public boolean isAutoThread() {
        return autoThread;
    }

    public void setAutoThread(boolean autoThread) {
        this.autoThread = autoThread;
    }

    public PanelTemplate getPanel() {
        return panel;
    }

    public void setPanel(PanelTemplate panel) {
        this.panel = panel;
    }

    public String getCounter() {
        return counter;
    }

    public void setCounter(String counter) {
        this.counter = counter;
    }
}
