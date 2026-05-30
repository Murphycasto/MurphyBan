package com.murphy.ban.util;

import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;

public final class PunishmentFormatter {

    private PunishmentFormatter() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\u00A7[0-9a-fA-FklmnorKLMNOR]", "");
    }

    public static String resolveStatus(Punishment p) {
        if (p.type() == PunishmentType.KICK) {
            return "<yellow>Issued</yellow>";
        }
        if (p.isRevoked()) {
            return "<gold>Revoked</gold>";
        }
        if (p.active() && !p.isExpired()) {
            return "<green>Active</green>";
        }
        return "<gray>Expired</gray>";
    }
}
