package com.thalhuria.bot.config;

/**
 * Cargo de recrutador concedido ao atingir um número de convites válidos.
 */
public class InviteTier {
    private int invites = 0;
    private String roleKey;

    public InviteTier() {}

    public int getInvites() {
        return invites;
    }

    public void setInvites(int invites) {
        this.invites = invites;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }
}
