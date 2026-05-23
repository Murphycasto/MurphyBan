package com.murphy.ban.manager;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MessageManager;
import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.BanLogger;
import com.murphy.ban.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PunishmentService {

    private final MurphyBan plugin;
    private final DatabaseManager db;
    private final MuteCache muteCache;
    private final ConfigManager config;
    private final MessageManager messages;

    public PunishmentService(MurphyBan plugin,
                             DatabaseManager db,
                             MuteCache muteCache,
                             ConfigManager config,
                             MessageManager messages) {
        this.plugin = plugin;
        this.db = db;
        this.muteCache = muteCache;
        this.config = config;
        this.messages = messages;
    }

    public CompletableFuture<Void> ban(OfflinePlayer target, String reason, long expiresAt, String issuedBy) {
        UUID uuid = target.getUniqueId();
        String name = nameOf(target);
        Punishment p = build(uuid, name, null, PunishmentType.BAN, reason, issuedBy, expiresAt);
        BanLogger.debug("PunishmentService.ban: uuid=" + uuid + " name=" + name
                + " expiresAt=" + expiresAt + " issuedBy=" + issuedBy);
        return expireExistingThenSave(uuid, PunishmentType.BAN, p)
                .thenRun(() -> {
                    notifyTargetThenKick(uuid, name, p, "punishment-notify-ban");
                    broadcastStaff("staff-alert-ban", p, issuedBy);
                })
                .whenComplete((v, ex) -> logIfFailed("ban", ex));
    }

    public CompletableFuture<Void> ipBan(OfflinePlayer target, String reason, long expiresAt, String issuedBy) {
        UUID uuid = target.getUniqueId();
        String name = nameOf(target);
        Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
        CompletableFuture<Optional<String>> ipLookup = online != null
                ? CompletableFuture.completedFuture(Optional.of(online.getAddress().getAddress().getHostAddress()))
                : db.getLastKnownIP(uuid);
        BanLogger.debug("PunishmentService.ipBan: uuid=" + uuid + " name=" + name
                + " expiresAt=" + expiresAt + " issuedBy=" + issuedBy
                + " ipSource=" + (online != null ? "online" : "lastKnown"));
        return ipLookup.thenCompose(maybeIp -> {
            if (maybeIp.isEmpty()) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("No known IP for " + name));
                return failed;
            }
            String ip = maybeIp.get();
            Punishment p = build(uuid, name, ip, PunishmentType.IP_BAN, reason, issuedBy, expiresAt);
            return db.getActiveIPPunishment(ip, PunishmentType.IP_BAN)
                    .thenCompose(existing -> existing.isPresent()
                            ? db.expirePunishment(existing.get().id())
                            : CompletableFuture.completedFuture(null))
                    .thenCompose(v -> db.savePunishment(p))
                    .thenRun(() -> {
                        notifyTargetThenKick(uuid, name, p, "punishment-notify-ban");
                        broadcastStaff("staff-alert-ban", p, issuedBy);
                    });
        }).whenComplete((v, ex) -> logIfFailed("ipBan", ex));
    }

    public CompletableFuture<Void> ipBanByAddress(String ip, String reason, long expiresAt, String issuedBy) {
        UUID placeholder = new UUID(0L, 0L);
        Punishment p = build(placeholder, ip, ip, PunishmentType.IP_BAN, reason, issuedBy, expiresAt);
        BanLogger.debug("PunishmentService.ipBanByAddress: ip=" + ip + " expiresAt=" + expiresAt
                + " issuedBy=" + issuedBy);
        return db.getActiveIPPunishment(ip, PunishmentType.IP_BAN)
                .thenCompose(existing -> existing.isPresent()
                        ? db.expirePunishment(existing.get().id())
                        : CompletableFuture.completedFuture(null))
                .thenCompose(v -> db.savePunishment(p))
                .thenCompose(v -> db.getAccountsOnIP(ip))
                .thenAccept(uuids -> {
                    broadcastStaff("staff-alert-ban", p, issuedBy);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (UUID u : uuids) {
                            Player online = Bukkit.getPlayer(u);
                            if (online != null && online.isOnline()) {
                                Map<String, String> ph = new HashMap<>();
                                ph.put("player", online.getName());
                                ph.put("reason", reason);
                                ph.put("expires", p.getFormattedExpiry());
                                ph.put("issued_by", issuedBy);
                                ph.put("duration", p.getFormattedDuration());
                                Component banScreen = messages.get("ban-screen", ph);
                                online.kickPlayer(LegacyComponentSerializer.legacySection().serialize(banScreen));
                            }
                        }
                    });
                })
                .whenComplete((v, ex) -> logIfFailed("ipBanByAddress", ex));
    }

    public CompletableFuture<Void> mute(OfflinePlayer target, String reason, long expiresAt, String issuedBy) {
        UUID uuid = target.getUniqueId();
        String name = nameOf(target);
        Punishment p = build(uuid, name, null, PunishmentType.MUTE, reason, issuedBy, expiresAt);
        BanLogger.debug("PunishmentService.mute: uuid=" + uuid + " name=" + name
                + " expiresAt=" + expiresAt + " issuedBy=" + issuedBy);
        return expireExistingThenSave(uuid, PunishmentType.MUTE, p)
                .thenRun(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        muteCache.put(uuid, p);
                        notifyTarget(online, "punishment-notify-mute", reasonPlaceholders(p, issuedBy));
                    }
                    broadcastStaff("staff-alert-mute", p, issuedBy);
                })
                .whenComplete((v, ex) -> logIfFailed("mute", ex));
    }

    public CompletableFuture<Void> kick(OfflinePlayer target, String reason, String issuedBy) {
        UUID uuid = target.getUniqueId();
        String name = nameOf(target);
        Punishment p = build(uuid, name, null, PunishmentType.KICK, reason, issuedBy, -1L);
        BanLogger.debug("PunishmentService.kick: uuid=" + uuid + " name=" + name
                + " issuedBy=" + issuedBy);
        return db.savePunishment(p).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    Component screen = messages.get("kick-message", Map.of("reason", reason));
                    online.kickPlayer(LegacyComponentSerializer.legacySection().serialize(screen));
                }
            });
            broadcastStaff("staff-alert-kick", p, issuedBy);
        }).whenComplete((v, ex) -> logIfFailed("kick", ex));
    }

    public CompletableFuture<WarnResult> warn(OfflinePlayer target, String reason, String issuedBy) {
        UUID uuid = target.getUniqueId();
        String name = nameOf(target);
        Punishment p = build(uuid, name, null, PunishmentType.WARN, reason, issuedBy, -1L);
        BanLogger.debug("PunishmentService.warn: uuid=" + uuid + " name=" + name
                + " issuedBy=" + issuedBy);
        return db.savePunishment(p)
                .thenCompose(v -> db.getHistory(uuid))
                .thenCompose(history -> {
                    List<Punishment> activeWarnList = filterAgeActive(history.stream()
                            .filter(h -> h.type() == PunishmentType.WARN && h.active() && !h.isExpired())
                            .toList());
                    int activeWarns = activeWarnList.size();
                    int threshold = config.getWarnThreshold();
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        notifyTarget(online, "punishment-notify-warn", reasonPlaceholders(p, issuedBy));
                        notifyTarget(online, "warn-count-notify", Map.of(
                                "count", String.valueOf(activeWarns),
                                "threshold", String.valueOf(threshold)));
                    }
                    broadcastStaff("staff-alert-warn", p, issuedBy);

                    if (activeWarns < threshold) {
                        return CompletableFuture.completedFuture(WarnResult.notTriggered(activeWarns));
                    }
                    String type = config.getAutoPunishType().toLowerCase();
                    long duration;
                    try {
                        duration = DurationParser.parse(config.getAutoPunishDuration());
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().log(Level.WARNING,
                                "Invalid warns.auto-punish-duration; defaulting to permanent.", ex);
                        duration = -1L;
                    }
                    long autoExpiresAt = duration < 0L ? -1L : System.currentTimeMillis() + duration;
                    String autoReason = "Auto-punish: reached " + threshold + " warnings";
                    CompletableFuture<Void> autoFuture = type.equals("ban")
                            ? ban(target, autoReason, autoExpiresAt, "MurphyBan")
                            : mute(target, autoReason, autoExpiresAt, "MurphyBan");
                    String action = type.equals("ban") ? "banned" : "muted";
                    return autoFuture
                            .thenCompose(v2 -> resetWarns(activeWarnList))
                            .thenApply(v3 -> WarnResult.triggered(activeWarns, action));
                })
                .whenComplete((v, ex) -> logIfFailed("warn", ex));
    }

    /**
     * Filters warns whose {@code issuedAt} is older than {@code warns.expire-after}. When the
     * configured value is {@code 0} (never expire), the input list is returned unchanged.
     */
    private List<Punishment> filterAgeActive(List<Punishment> warns) {
        long expireAfter = config.getWarnExpireAfter();
        if (expireAfter <= 0L) {
            return warns;
        }
        long cutoff = System.currentTimeMillis() - expireAfter;
        return warns.stream()
                .filter(w -> w.issuedAt() >= cutoff)
                .toList();
    }

    private CompletableFuture<Void> resetWarns(List<Punishment> activeWarns) {
        if (activeWarns.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = activeWarns.stream()
                .map(w -> db.expirePunishment(w.id()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<Boolean> unban(OfflinePlayer target, String issuedBy) {
        BanLogger.debug("PunishmentService.unban: uuid=" + target.getUniqueId() + " issuedBy=" + issuedBy);
        return db.getActivePunishment(target.getUniqueId(), PunishmentType.BAN)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return db.revokePunishment(existing.get().id(), issuedBy).thenApply(v -> true);
                })
                .whenComplete((v, ex) -> logIfFailed("unban", ex));
    }

    public CompletableFuture<Boolean> unIPBan(String ip, String issuedBy) {
        BanLogger.debug("PunishmentService.unIPBan: ip=" + ip + " issuedBy=" + issuedBy);
        return db.getActiveIPPunishment(ip, PunishmentType.IP_BAN)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return db.revokePunishment(existing.get().id(), issuedBy).thenApply(v -> true);
                })
                .whenComplete((v, ex) -> logIfFailed("unIPBan", ex));
    }

    public CompletableFuture<Boolean> unmute(OfflinePlayer target, String issuedBy) {
        UUID uuid = target.getUniqueId();
        BanLogger.debug("PunishmentService.unmute: uuid=" + uuid + " issuedBy=" + issuedBy);
        return db.getActivePunishment(uuid, PunishmentType.MUTE)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        muteCache.invalidate(uuid);
                        return CompletableFuture.completedFuture(false);
                    }
                    return db.revokePunishment(existing.get().id(), issuedBy).thenApply(v -> {
                        muteCache.invalidate(uuid);
                        return true;
                    });
                })
                .whenComplete((v, ex) -> logIfFailed("unmute", ex));
    }

    public CompletableFuture<Void> unwarn(int punishmentId, String issuedBy) {
        BanLogger.debug("PunishmentService.unwarn: id=" + punishmentId + " issuedBy=" + issuedBy);
        return db.revokePunishment(punishmentId, issuedBy)
                .whenComplete((v, ex) -> logIfFailed("unwarn", ex));
    }

    private CompletableFuture<Void> expireExistingThenSave(UUID uuid, PunishmentType type, Punishment toSave) {
        return db.getActivePunishment(uuid, type)
                .thenCompose(existing -> existing.isPresent()
                        ? db.expirePunishment(existing.get().id())
                        : CompletableFuture.completedFuture(null))
                .thenCompose(v -> db.savePunishment(toSave));
    }

    private Punishment build(UUID uuid, String name, String ip, PunishmentType type,
                             String reason, String issuedBy, long expiresAt) {
        return new Punishment(
                0,
                uuid,
                name,
                ip,
                type,
                reason,
                issuedBy,
                System.currentTimeMillis(),
                expiresAt,
                true,
                null,
                -1L);
    }

    private void notifyTargetThenKick(UUID uuid, String name, Punishment p, String messageKey) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                return;
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("player", name);
            ph.put("reason", p.reason());
            ph.put("expires", p.getFormattedExpiry());
            ph.put("issued_by", p.issuedBy());
            ph.put("duration", p.getFormattedDuration());
            Component banScreen = messages.get("ban-screen", ph);
            online.kickPlayer(LegacyComponentSerializer.legacySection().serialize(banScreen));
        });
    }

    private void notifyTarget(Player online, String messageKey, Map<String, String> placeholders) {
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getAudiences().player(online).sendMessage(messages.get(messageKey, placeholders)));
    }

    private void broadcastStaff(String messageKey, Punishment p, String issuedBy) {
        Map<String, String> ph = Map.of(
                "player", p.playerName(),
                "staff", issuedBy,
                "reason", p.reason());
        Component msg = messages.get(messageKey, ph);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("murphyban.alerts")) {
                    plugin.getAudiences().player(online).sendMessage(msg);
                }
            }
        });
    }

    private Map<String, String> reasonPlaceholders(Punishment p, String issuedBy) {
        return Map.of(
                "reason", p.reason(),
                "staff", issuedBy,
                "expires", p.getFormattedExpiry());
    }

    /**
     * Surfaces internal CompletableFuture failures from this service. Attached via
     * {@code whenComplete} so the original exception still propagates to callers; this
     * is purely an observability hook so a swallowed exceptionally upstream is impossible.
     */
    private void logIfFailed(String op, Throwable ex) {
        if (ex == null) {
            return;
        }
        plugin.getLogger().log(Level.SEVERE,
                "[MurphyBan] PunishmentService." + op + " failed: " + ex.getMessage(), ex);
        ex.printStackTrace();
    }

    private String nameOf(OfflinePlayer target) {
        String n = target.getName();
        return n == null ? target.getUniqueId().toString() : n;
    }

    public List<Punishment> filterActiveWarns(List<Punishment> history) {
        return history.stream()
                .filter(h -> h.type() == PunishmentType.WARN && h.active() && !h.isExpired())
                .toList();
    }
}