package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class MurphyBanCommand implements CommandExecutor {

    private static final String[] ABOUT_LINES = {
            "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>",
            "<red><bold>MurphyBan</bold></red> <gray>v{version}</gray>",
            "<gray>A powerful punishment management plugin.</gray>",
            "<gray>Author: <white>Murphycasto</white></gray>",
            "<gray>GitHub: <white><click:open_url:'https://github.com/murphy/MurphyBan'><hover:show_text:'<gray>Click to open</gray>'>github.com/murphy/MurphyBan</hover></click></white></gray>",
            "<gray>Database: <white>{db_type}</white> <green>({status})</green></gray>",
            "<gray>Server: <white>{mc_version}</white></gray>",
            "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>"
    };

    private final MurphyBan plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MurphyBanCommand(MurphyBan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("about")) {
            handleAbout(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }
        plugin.getAudiences().sender(sender).sendMessage(miniMessage.deserialize(
                "<red>Unknown subcommand. Try <white>/" + label + " about</white> or <white>/" + label + " reload</white>."));
        return true;
    }

    private void handleAbout(CommandSender sender) {
        DatabaseManager db = MurphyBan.getDatabase();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("version", plugin.getDescription().getVersion());
        placeholders.put("db_type", plugin.getConfigManager().getDatabaseType());
        placeholders.put("status", db != null ? "Connected" : "Disconnected");
        placeholders.put("mc_version", Bukkit.getVersion());

        Audience audience = plugin.getAudiences().sender(sender);
        for (String line : ABOUT_LINES) {
            String filled = line;
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                filled = filled.replace("{" + e.getKey() + "}", e.getValue());
            }
            Component c = miniMessage.deserialize(filled);
            audience.sendMessage(c);
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("murphyban.admin")) {
            sendKey(sender, "no-permission");
            return;
        }
        try {
            plugin.reloadConfig();
            plugin.getMessageManager().reload();
            sendKey(sender, "reload-success");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", ex);
            sendKey(sender, "generic-error");
        }
    }

    private void sendKey(CommandSender sender, String key) {
        Component msg = plugin.getMessageManager().get(key);
        plugin.getAudiences().sender(sender).sendMessage(msg);
    }
}
