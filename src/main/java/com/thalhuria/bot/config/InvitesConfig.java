package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Rastreio de convites: quem convidou quem, recompensa em moedas e cargos de recrutador.
 */
public class InvitesConfig {
    private boolean enabled = true;
    private boolean showInWelcome = true;
    private int coinsPerInvite = 200;
    private boolean rewardOnlyVerified = true;
    private int minAccountAgeDays = 7;
    private boolean removeTierOnLeave = true;
    private List<InviteTier> tiers = new ArrayList<>();

    public InvitesConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShowInWelcome() {
        return showInWelcome;
    }

    public void setShowInWelcome(boolean showInWelcome) {
        this.showInWelcome = showInWelcome;
    }

    public int getCoinsPerInvite() {
        return coinsPerInvite;
    }

    public void setCoinsPerInvite(int coinsPerInvite) {
        this.coinsPerInvite = coinsPerInvite;
    }

    public boolean isRewardOnlyVerified() {
        return rewardOnlyVerified;
    }

    public void setRewardOnlyVerified(boolean rewardOnlyVerified) {
        this.rewardOnlyVerified = rewardOnlyVerified;
    }

    public int getMinAccountAgeDays() {
        return minAccountAgeDays;
    }

    public void setMinAccountAgeDays(int minAccountAgeDays) {
        this.minAccountAgeDays = minAccountAgeDays;
    }

    public boolean isRemoveTierOnLeave() {
        return removeTierOnLeave;
    }

    public void setRemoveTierOnLeave(boolean removeTierOnLeave) {
        this.removeTierOnLeave = removeTierOnLeave;
    }

    public List<InviteTier> getTiers() {
        return tiers;
    }

    public void setTiers(List<InviteTier> tiers) {
        this.tiers = tiers != null ? tiers : new ArrayList<>();
    }
}
