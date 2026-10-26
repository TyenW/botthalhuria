package com.thalhuria.bot.config;

/**
 * Cargo concedido ao atingir um nível.
 */
public class LevelReward {
    private int level = 0;
    private String roleKey;

    public LevelReward() {}

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }
}
