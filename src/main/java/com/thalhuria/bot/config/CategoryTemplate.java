package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

public class CategoryTemplate {
    private String key;
    private String name;
    private int position = 0;
    private boolean isIndividualRoomsCategory = false;
    private List<PermissionRule> permissionOverrides = new ArrayList<>();
    private List<ChannelTemplate> channels = new ArrayList<>();

    public CategoryTemplate() {}

    public CategoryTemplate(String key, String name, int position) {
        this.key = key;
        this.name = name;
        this.position = position;
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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isIndividualRoomsCategory() {
        return isIndividualRoomsCategory;
    }

    public void setIndividualRoomsCategory(boolean individualRoomsCategory) {
        isIndividualRoomsCategory = individualRoomsCategory;
    }

    public List<PermissionRule> getPermissionOverrides() {
        return permissionOverrides;
    }

    public void setPermissionOverrides(List<PermissionRule> permissionOverrides) {
        this.permissionOverrides = permissionOverrides;
    }

    public List<ChannelTemplate> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelTemplate> channels) {
        this.channels = channels;
    }
}
