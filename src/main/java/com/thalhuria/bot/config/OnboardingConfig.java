package com.thalhuria.bot.config;

/**
 * Fluxo de entrada de novos membros: cargo automático, boas-vindas e verificação por botão.
 * Todos os campos *Key referenciam a "key" de um cargo ou canal do template.
 */
public class OnboardingConfig {
    private boolean enabled = true;
    private String autoRoleKey;
    private String botRoleKey;
    private String verifiedRoleKey;
    private boolean removeAutoRoleOnVerify = true;
    private String welcomeChannelKey;
    private boolean pingOnWelcome = true;
    private String welcomeTitle = "👋 Bem-vindo(a)!";
    private String welcomeMessage = "Olá {user}, que bom ter você em **{server}**!";
    private String welcomeImageUrl;
    private String verifiedMessage = "✅ Verificação concluída! Aproveite o servidor.";
    // Servidores com Comunidade exigem um canal de regras e um de atualizações; o setup aponta-os para estes canais
    private String rulesChannelKey;
    private String communityUpdatesChannelKey;
    // Mensagem privada de boas-vindas
    private boolean dmWelcome = false;
    private String dmTitle = "Bem-vindo(a) a {server}!";
    private String dmMessage;
    private String dmButtonChannelKey;

    public OnboardingConfig() {}

    public String getRulesChannelKey() {
        return rulesChannelKey;
    }

    public void setRulesChannelKey(String rulesChannelKey) {
        this.rulesChannelKey = rulesChannelKey;
    }

    public String getCommunityUpdatesChannelKey() {
        return communityUpdatesChannelKey;
    }

    public void setCommunityUpdatesChannelKey(String communityUpdatesChannelKey) {
        this.communityUpdatesChannelKey = communityUpdatesChannelKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAutoRoleKey() {
        return autoRoleKey;
    }

    public void setAutoRoleKey(String autoRoleKey) {
        this.autoRoleKey = autoRoleKey;
    }

    public String getBotRoleKey() {
        return botRoleKey;
    }

    public void setBotRoleKey(String botRoleKey) {
        this.botRoleKey = botRoleKey;
    }

    public String getVerifiedRoleKey() {
        return verifiedRoleKey;
    }

    public void setVerifiedRoleKey(String verifiedRoleKey) {
        this.verifiedRoleKey = verifiedRoleKey;
    }

    public boolean isRemoveAutoRoleOnVerify() {
        return removeAutoRoleOnVerify;
    }

    public void setRemoveAutoRoleOnVerify(boolean removeAutoRoleOnVerify) {
        this.removeAutoRoleOnVerify = removeAutoRoleOnVerify;
    }

    public String getWelcomeChannelKey() {
        return welcomeChannelKey;
    }

    public void setWelcomeChannelKey(String welcomeChannelKey) {
        this.welcomeChannelKey = welcomeChannelKey;
    }

    public boolean isPingOnWelcome() {
        return pingOnWelcome;
    }

    public void setPingOnWelcome(boolean pingOnWelcome) {
        this.pingOnWelcome = pingOnWelcome;
    }

    public String getWelcomeTitle() {
        return welcomeTitle;
    }

    public void setWelcomeTitle(String welcomeTitle) {
        this.welcomeTitle = welcomeTitle;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getWelcomeImageUrl() {
        return welcomeImageUrl;
    }

    public void setWelcomeImageUrl(String welcomeImageUrl) {
        this.welcomeImageUrl = welcomeImageUrl;
    }

    public String getVerifiedMessage() {
        return verifiedMessage;
    }

    public void setVerifiedMessage(String verifiedMessage) {
        this.verifiedMessage = verifiedMessage;
    }

    public boolean isDmWelcome() {
        return dmWelcome;
    }

    public void setDmWelcome(boolean dmWelcome) {
        this.dmWelcome = dmWelcome;
    }

    public String getDmTitle() {
        return dmTitle;
    }

    public void setDmTitle(String dmTitle) {
        this.dmTitle = dmTitle;
    }

    public String getDmMessage() {
        return dmMessage;
    }

    public void setDmMessage(String dmMessage) {
        this.dmMessage = dmMessage;
    }

    public String getDmButtonChannelKey() {
        return dmButtonChannelKey;
    }

    public void setDmButtonChannelKey(String dmButtonChannelKey) {
        this.dmButtonChannelKey = dmButtonChannelKey;
    }
}
