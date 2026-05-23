package com.murphy.ban.model;

public enum PunishmentType {

    BAN("Ban", true),
    IP_BAN("IP Ban", true),
    MUTE("Mute", true),
    KICK("Kick", false),
    WARN("Warn", false);

    private final String displayName;
    private final boolean permanentCapable;

    PunishmentType(String displayName, boolean permanentCapable) {
        this.displayName = displayName;
        this.permanentCapable = permanentCapable;
    }

    public boolean isPermanentCapable() {
        return permanentCapable;
    }

    public String getDisplayName() {
        return displayName;
    }
}