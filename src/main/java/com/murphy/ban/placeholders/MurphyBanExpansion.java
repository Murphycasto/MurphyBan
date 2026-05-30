package com.murphy.ban.placeholders;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.PunishmentFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MurphyBanExpansion extends PlaceholderExpansion {

    private final MurphyBan plugin;

    public MurphyBanExpansion(MurphyBan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "murphyban";
    }

    @Override
    public String getAuthor() {
        return "Murphycasto";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        DatabaseManager db = MurphyBan.getDatabase();
        if (db == null) {
            return "";
        }
        return switch (identifier.toLowerCase()) {
            case "total_banned" -> safeInt(db.countActivePunishments(PunishmentType.BAN));
            case "total_muted" -> safeInt(db.countActivePunishments(PunishmentType.MUTE));
            case "banned" -> player == null ? "false" : Boolean.toString(activeBan(db, player.getUniqueId()).isPresent());
            case "banned_reason" -> player == null ? "" : activeBan(db, player.getUniqueId()).map(p -> PunishmentFormatter.sanitize(p.reason())).orElse("");
            case "banned_expires" -> player == null ? "" : activeBan(db, player.getUniqueId()).map(Punishment::getFormattedExpiry).orElse("");
            case "muted" -> player == null ? "false" : Boolean.toString(activeMute(db, player).isPresent());
            case "muted_reason" -> player == null ? "" : activeMute(db, player).map(p -> PunishmentFormatter.sanitize(p.reason())).orElse("");
            case "muted_expires" -> player == null ? "" : activeMute(db, player).map(Punishment::getFormattedExpiry).orElse("");
            case "warns" -> player == null ? "0" : Integer.toString(countActiveWarns(db, player.getUniqueId()));
            default -> null;
        };
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        return onRequest(player, identifier);
    }

    private Optional<Punishment> activeBan(DatabaseManager db, UUID uuid) {
        return joinPunishment(db, uuid, PunishmentType.BAN);
    }

    private Optional<Punishment> activeMute(DatabaseManager db, OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        Optional<Punishment> cached = plugin.getMuteCache().get(uuid);
        if (cached.isPresent()) {
            return cached;
        }
        return joinPunishment(db, uuid, PunishmentType.MUTE);
    }

    private Optional<Punishment> joinPunishment(DatabaseManager db, UUID uuid, PunishmentType type) {
        try {
            Optional<Punishment> p = db.getActivePunishment(uuid, type).get();
            return p.filter(x -> !x.isExpired());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException ex) {
            plugin.getLogger().warning("Placeholder lookup failed for " + uuid + " type=" + type + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    private int countActiveWarns(DatabaseManager db, UUID uuid) {
        try {
            return (int) db.getHistory(uuid).get().stream()
                    .filter(p -> p.type() == PunishmentType.WARN && p.active() && !p.isExpired())
                    .count();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException ex) {
            plugin.getLogger().warning("Warn count lookup failed for " + uuid + ": " + ex.getMessage());
            return 0;
        }
    }

    private String safeInt(java.util.concurrent.CompletableFuture<Integer> future) {
        try {
            return Integer.toString(future.get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "0";
        } catch (ExecutionException ex) {
            plugin.getLogger().warning("countActivePunishments failed: " + ex.getMessage());
            return "0";
        }
    }
}
