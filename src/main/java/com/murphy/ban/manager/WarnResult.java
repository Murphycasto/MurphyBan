package com.murphy.ban.manager;

public record WarnResult(int warnCount, boolean autoPunished, String autoPunishAction) {

    public static WarnResult notTriggered(int count) {
        return new WarnResult(count, false, null);
    }

    public static WarnResult triggered(int count, String action) {
        return new WarnResult(count, true, action);
    }
}