package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Onde os comandos slash do bot podem ser usados. Quem tem ADMINISTRATOR ou um dos bypassRoleKeys
 * usa em qualquer canal; os demais só nos canais de allowedChannelKeys (e nos tópicos deles).
 * Lista vazia ou canais ainda não criados liberam todos os canais.
 */
public class CommandsConfig {
    private boolean restrictToChannels = true;
    private List<String> allowedChannelKeys = new ArrayList<>();
    private List<String> bypassRoleKeys = new ArrayList<>();

    public CommandsConfig() {}

    public boolean isRestrictToChannels() {
        return restrictToChannels;
    }

    public void setRestrictToChannels(boolean restrictToChannels) {
        this.restrictToChannels = restrictToChannels;
    }

    public List<String> getAllowedChannelKeys() {
        return allowedChannelKeys;
    }

    public void setAllowedChannelKeys(List<String> allowedChannelKeys) {
        this.allowedChannelKeys = allowedChannelKeys != null ? allowedChannelKeys : new ArrayList<>();
    }

    public List<String> getBypassRoleKeys() {
        return bypassRoleKeys;
    }

    public void setBypassRoleKeys(List<String> bypassRoleKeys) {
        this.bypassRoleKeys = bypassRoleKeys != null ? bypassRoleKeys : new ArrayList<>();
    }
}
