package com.murphy.ban.listeners;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerChatListener implements Listener {

    private final MurphyBan plugin;

    public PlayerChatListener(MurphyBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        DatabaseManager db = MurphyBan.getDatabase();
        if (db == null) {
            return;
        }

        Optional<Punishment> mute = db.getActivePunishment(uuid, PunishmentType.MUTE).join();
        if (mute.isEmpty()) {
            plugin.getMuteCache().invalidate(uuid);
            return;
        }
        Punishment p = mute.get();
        if (p.isExpired()) {
            plugin.getMuteCache().invalidate(uuid);
            db.expirePunishment(p.id()).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "expirePunishment failed for id=" + p.id(), ex);
                return null;
            });
            return;
        }

        plugin.getMuteCache().put(uuid, p);
        event.setCancelled(true);
        sendMuteScreen(player, p);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Optional<Punishment> cached = plugin.getMuteCache().get(player.getUniqueId());
        if (cached.isEmpty()) {
            return;
        }
        String firstWord = extractCommand(event.getMessage());
        ConfigManager cfg = plugin.getConfigManager();
        List<String> whitelist = cfg.getMuteCommandWhitelist();
        if (whitelist.stream().anyMatch(w -> w.equalsIgnoreCase(firstWord))) {
            return;
        }
        event.setCancelled(true);
        sendMuteScreen(player, cached.get());
    }

    private void sendMuteScreen(Player player, Punishment mute) {
        Map<String, String> placeholders = Map.of(
                "reason", mute.reason(),
                "expires", mute.getFormattedExpiry(),
                "remaining", mute.getFormattedExpiry());
        Component msg = plugin.getMessageManager().get("mute-screen", placeholders);
        plugin.getAudiences().player(player).sendMessage(msg);
    }

    private String extractCommand(String message) {
        if (message.isEmpty()) {
            return "";
        }
        int slashOffset = message.startsWith("/") ? 1 : 0;
        int spaceIdx = message.indexOf(' ', slashOffset);
        if (spaceIdx < 0) {
            return message.substring(slashOffset).toLowerCase(Locale.ROOT);
        }
        return message.substring(slashOffset, spaceIdx).toLowerCase(Locale.ROOT);
    }
}