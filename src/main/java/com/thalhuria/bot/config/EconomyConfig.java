package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Economia leve: moedas por atividade, recompensa diária e loja de cargos.
 */
public class EconomyConfig {
    private boolean enabled = true;
    private String currencyName = "Thalers";
    private String currencyEmoji = "🪙";
    private int coinsPerMessage = 1;
    private int coinsPerVoiceMinute = 1;
    private int dailyAmount = 100;
    private int dailyStreakBonus = 10;
    private int maxStreakBonusDays = 7;
    private int dailyXp = 50;
    private int dailyLuckyChance = 10;
    private int dailyLuckyMultiplier = 3;
    private int dailyWeeklyBonus = 500;
    private List<ShopItem> shop = new ArrayList<>();

    public EconomyConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public String getCurrencyEmoji() {
        return currencyEmoji;
    }

    public void setCurrencyEmoji(String currencyEmoji) {
        this.currencyEmoji = currencyEmoji;
    }

    public int getCoinsPerMessage() {
        return coinsPerMessage;
    }

    public void setCoinsPerMessage(int coinsPerMessage) {
        this.coinsPerMessage = coinsPerMessage;
    }

    public int getCoinsPerVoiceMinute() {
        return coinsPerVoiceMinute;
    }

    public void setCoinsPerVoiceMinute(int coinsPerVoiceMinute) {
        this.coinsPerVoiceMinute = coinsPerVoiceMinute;
    }

    public int getDailyAmount() {
        return dailyAmount;
    }

    public void setDailyAmount(int dailyAmount) {
        this.dailyAmount = dailyAmount;
    }

    public int getDailyStreakBonus() {
        return dailyStreakBonus;
    }

    public void setDailyStreakBonus(int dailyStreakBonus) {
        this.dailyStreakBonus = dailyStreakBonus;
    }

    public int getMaxStreakBonusDays() {
        return maxStreakBonusDays;
    }

    public void setMaxStreakBonusDays(int maxStreakBonusDays) {
        this.maxStreakBonusDays = maxStreakBonusDays;
    }

    public List<ShopItem> getShop() {
        return shop;
    }

    public void setShop(List<ShopItem> shop) {
        this.shop = shop != null ? shop : new ArrayList<>();
    }

    public int getDailyXp() {
        return dailyXp;
    }

    public void setDailyXp(int dailyXp) {
        this.dailyXp = dailyXp;
    }

    public int getDailyLuckyChance() {
        return dailyLuckyChance;
    }

    public void setDailyLuckyChance(int dailyLuckyChance) {
        this.dailyLuckyChance = dailyLuckyChance;
    }

    public int getDailyLuckyMultiplier() {
        return dailyLuckyMultiplier;
    }

    public void setDailyLuckyMultiplier(int dailyLuckyMultiplier) {
        this.dailyLuckyMultiplier = dailyLuckyMultiplier;
    }

    public int getDailyWeeklyBonus() {
        return dailyWeeklyBonus;
    }

    public void setDailyWeeklyBonus(int dailyWeeklyBonus) {
        this.dailyWeeklyBonus = dailyWeeklyBonus;
    }
}
