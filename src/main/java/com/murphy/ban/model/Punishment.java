package com.murphy.ban.model;

import com.murphy.ban.util.DurationParser;

import java.util.UUID;

public record Punishment(
        int id,
        UUID uuid,
        String playerName,
        String ip,
        PunishmentType type,
        String reason,
        String issuedBy,
        long issuedAt,
        long expiresAt,
        boolean active,
        String revokedBy,
        long revokedAt
) {

    public boolean isPermanent() {
        return expiresAt == -1L;
    }

    // Wall-clock check only; does not consult `active`. A row can be active in the DB and expired by time.
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() > expiresAt;
    }

    public boolean isRevoked() {
        return revokedBy != null;
    }

    public String getFormattedExpiry() {
        return DurationParser.formatExpiry(expiresAt);
    }

    public String getFormattedDuration() {
        if (isPermanent()) {
            return "Permanent";
        }
        return DurationParser.format(expiresAt - issuedAt);
    }
}
