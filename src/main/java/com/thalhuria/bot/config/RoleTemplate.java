package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

public class RoleTemplate {
    private String key;
    private String name;
    private String colorHex;
    private boolean hoist = true;
    private boolean mentionable = false;
    private boolean isMemberRole = false;
    private boolean isAdminRole = false;
    private boolean ownerRole = false;
    private List<String> permissions = new ArrayList<>();

    // Cargos de autoatribuição (menu de cargos)
    private boolean selfAssignable = false;
    private String emoji;
    private String description;

    public RoleTemplate() {}

    public RoleTemplate(String key, String name, String colorHex, boolean hoist, boolean mentionable, List<String> permissions) {
        this.key = key;
        this.name = name;
        this.colorHex = colorHex;
        this.hoist = hoist;
        this.mentionable = mentionable;
        this.permissions = permissions != null ? permissions : new ArrayList<>();
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

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isHoist() {
        return hoist;
    }

    public void setHoist(boolean hoist) {
        this.hoist = hoist;
    }

    public boolean isMentionable() {
        return mentionable;
    }

    public void setMentionable(boolean mentionable) {
        this.mentionable = mentionable;
    }

    public boolean isMemberRole() {
        return isMemberRole;
    }

    public void setMemberRole(boolean memberRole) {
        isMemberRole = memberRole;
    }

    public boolean isAdminRole() {
        return isAdminRole;
    }

    public void setAdminRole(boolean adminRole) {
        isAdminRole = adminRole;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public boolean isSelfAssignable() {
        return selfAssignable;
    }

    public void setSelfAssignable(boolean selfAssignable) {
        this.selfAssignable = selfAssignable;
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

    public boolean isOwnerRole() {
        return ownerRole;
    }

    public void setOwnerRole(boolean ownerRole) {
        this.ownerRole = ownerRole;
    }
}
