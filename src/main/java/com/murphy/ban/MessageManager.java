package com.murphy.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class MessageManager {

    private static final Set<String> NO_PREFIX_KEYS = Set.of("prefix", "ban-screen", "kick-message");

    private final MurphyBan plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private File messagesFile;

    public MessageManager(MurphyBan plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reload() {
        load();
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            plugin.getLogger().log(Level.WARNING, "Missing message key: " + key);
            return Component.text("<missing:" + key + ">");
        }

        if (!NO_PREFIX_KEYS.contains(key)) {
            String prefix = messages.getString("prefix", "");
            raw = raw.replace("{prefix}", prefix);
        } else {
            raw = raw.replace("{prefix}", "");
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return miniMessage.deserialize(raw);
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public String getRaw(String key) {
        return messages.getString(key, "");
    }
}