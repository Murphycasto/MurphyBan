package com.murphy.ban;

import com.murphy.ban.util.DurationParser;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ConfigManager {

    private static final long DEFAULT_WARN_EXPIRE_MILLIS = TimeUnit.DAYS.toMillis(30L);

    private FileConfiguration config() {
        return MurphyBan.getInstance().getConfig();
    }

    public boolean isDebugMode() {
        return config().getBoolean("debug", false);
    }

    public String getDatabaseType() {
        return config().getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config().getString("database.host", "localhost");
    }

    public int getMySQLPort() {
        return config().getInt("database.port", 3306);
    }

    public String getMySQLName() {
        return config().getString("database.name", "murphyban");
    }

    public String getMySQLUser() {
        return config().getString("database.username", "root");
    }

    public String getMySQLPassword() {
        return config().getString("database.password", "");
    }

    public List<String> getMuteCommandWhitelist() {
        if (config().contains("mute.command-whitelist")) {
            return config().getStringList("mute.command-whitelist");
        }
        return List.of("register", "login");
    }

    public boolean isAutoAltBan() {
        return config().getBoolean("alts.auto-ban", false);
    }

    public boolean isNotifyStaff() {
        return config().getBoolean("alts.notify-staff", true);
    }

    public int getWarnThreshold() {
        return config().getInt("warns.threshold", 3);
    }

    public String getAutoPunishType() {
        return config().getString("warns.auto-punish", "mute");
    }

    public String getAutoPunishDuration() {
        return config().getString("warns.auto-punish-duration", "1d");
    }

    /**
     * Returns the warn-age threshold in milliseconds. {@code 0} means warns never age out.
     * Invalid values fall back to 30 days with a logged warning.
     */
    public long getWarnExpireAfter() {
        String raw = config().getString("warns.expire-after");
        if (raw == null || raw.isBlank()) {
            MurphyBan.getInstance().getLogger().log(Level.WARNING,
                    "warns.expire-after is missing from config.yml; defaulting to 30 days.");
            return DEFAULT_WARN_EXPIRE_MILLIS;
        }
        String trimmed = raw.trim();
        if (trimmed.equals("0")) {
            return 0L;
        }
        try {
            long parsed = DurationParser.parse(trimmed);
            if (parsed < 0L) {
                return 0L;
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            MurphyBan.getInstance().getLogger().log(Level.WARNING,
                    "Invalid warns.expire-after '" + trimmed + "' in config.yml; defaulting to 30 days.");
            return DEFAULT_WARN_EXPIRE_MILLIS;
        }
    }
}