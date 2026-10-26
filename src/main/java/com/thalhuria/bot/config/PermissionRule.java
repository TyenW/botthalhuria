package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

public class PermissionRule {
    private String target; // "@everyone", "Admin", "Membro", etc.
    private List<String> allow = new ArrayList<>();
    private List<String> deny = new ArrayList<>();

    public PermissionRule() {}

    public PermissionRule(String target, List<String> allow, List<String> deny) {
        this.target = target;
        this.allow = allow != null ? allow : new ArrayList<>();
        this.deny = deny != null ? deny : new ArrayList<>();
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public List<String> getAllow() {
        return allow;
    }

    public void setAllow(List<String> allow) {
        this.allow = allow;
    }

    public List<String> getDeny() {
        return deny;
    }

    public void setDeny(List<String> deny) {
        this.deny = deny;
    }
}
