package com.murphy.ban.listeners;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PlayerLoginListener implements Listener {

    private final MurphyBan plugin;

    public PlayerLoginListener(MurphyBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();
        DatabaseManager db = MurphyBan.getDatabase();
        if (db == null) {
            return;
        }

        Optional<Punishment> ban = db.getActivePunishment(uuid, PunishmentType.BAN).join();
        if (ban.isPresent()) {
            Punishment p = ban.get();
            if (p.isExpired()) {
                db.expirePunishment(p.id()).exceptionally(ex -> logFailure("expirePunishment(BAN)", ex));
            } else {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(name, p));
                return;
            }
        }

        Optional<Punishment> ipBan = db.getActiveIPPunishment(ip, PunishmentType.IP_BAN).join();
        if (ipBan.isPresent()) {
            Punishment p = ipBan.get();
            if (p.isExpired()) {
                db.expirePunishment(p.id()).exceptionally(ex -> logFailure("expirePunishment(IP_BAN)", ex));
            } else {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(name, p));
                return;
            }
        }

        Optional<Punishment> mute = db.getActivePunishment(uuid, PunishmentType.MUTE).join();
        if (mute.isPresent() && !mute.get().isExpired()) {
            plugin.getMuteCache().put(uuid, mute.get());
        } else {
            plugin.getMuteCache().invalidate(uuid);
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = event.getAddress().getHostAddress();
        DatabaseManager db = MurphyBan.getDatabase();
        ConfigManager cfg = plugin.getConfigManager();
        if (db == null) {
            return;
        }

        db.saveIPHistory(uuid, name, ip)
                .exceptionally(ex -> logFailure("saveIPHistory", ex));

        db.getAccountsOnIP(ip)
                .thenCompose(accounts -> findBannedAlt(db, uuid, accounts))
                .thenAccept(maybeBan -> handleAltResult(maybeBan, uuid, name, ip, cfg, db))
                .exceptionally(ex -> logFailure("alt-detection chain", ex));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMuteCache().invalidate(event.getPlayer().getUniqueId());
    }

    private CompletableFuture<Optional<Punishment>> findBannedAlt(DatabaseManager db, UUID self, List<UUID> accounts) {
        List<UUID> others = accounts.stream().filter(u -> !u.equals(self)).toList();
        if (others.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        List<CompletableFuture<Optional<Punishment>>> futures = others.stream()
                .map(u -> db.getActivePunishment(u, PunishmentType.BAN))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(opt -> opt.isPresent() && !opt.get().isExpired())
                        .map(Optional::get)
                        .findFirst());
    }

    private void handleAltResult(Optional<Punishment> maybeBan, UUID uuid, String name, String ip,
                                 ConfigManager cfg, DatabaseManager db) {
        if (maybeBan.isEmpty()) {
            return;
        }
        Punishment originalBan = maybeBan.get();

        if (cfg.isNotifyStaff()) {
            Map<String, String> placeholders = Map.of(
                    "joiner", name,
                    "banned", originalBan.playerName());
            Bukkit.getScheduler().runTask(plugin, () -> broadcastAltAlert(placeholders));
        }

        if (cfg.isAutoAltBan()) {
            Punishment newBan = new Punishment(
                    0,
                    uuid,
                    name,
                    ip,
                    PunishmentType.BAN,
                    "Alt account of " + originalBan.playerName(),
                    "MurphyBan",
                    System.currentTimeMillis(),
                    originalBan.expiresAt(),
                    true,
                    null,
                    -1L);
            db.savePunishment(newBan)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> kickIfOnline(uuid, name, newBan)))
                    .exceptionally(ex -> logFailure("auto-alt-ban savePunishment", ex));
        }
    }

    private void kickIfOnline(UUID uuid, String name, Punishment newBan) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.kickPlayer(banScreen(name, newBan));
        }
    }

    private void broadcastAltAlert(Map<String, String> placeholders) {
        Component alert = plugin.getMessageManager().get("alt-alert", placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("murphyban.alerts")) {
                plugin.getAudiences().player(online).sendMessage(alert);
            }
        }
    }

    private String banScreen(String name, Punishment p) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", name);
        placeholders.put("reason", p.reason());
        placeholders.put("expires", p.getFormattedExpiry());
        placeholders.put("issued_by", p.issuedBy());
        placeholders.put("duration", p.getFormattedDuration());
        Component c = plugin.getMessageManager().get("ban-screen", placeholders);
        return LegacyComponentSerializer.legacySection().serialize(c);
    }

    private <T> T logFailure(String context, Throwable ex) {
        plugin.getLogger().log(Level.SEVERE, "PlayerLoginListener: " + context + " failed", ex);
        return null;
    }
}