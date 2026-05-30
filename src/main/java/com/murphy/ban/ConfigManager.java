package com.murphy.ban;

import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.DurationParser;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ConfigManager {

    private static final long DEFAULT_WARN_EXPIRE_MILLIS = TimeUnit.DAYS.toMillis(30L);

    private static final Map<PunishmentType, Material> DEFAULT_LIST_ITEMS = new EnumMap<>(PunishmentType.class);

    static {
        DEFAULT_LIST_ITEMS.put(PunishmentType.BAN, Material.RED_WOOL);
        DEFAULT_LIST_ITEMS.put(PunishmentType.IP_BAN, Material.MAGENTA_WOOL);
        DEFAULT_LIST_ITEMS.put(PunishmentType.MUTE, Material.ORANGE_WOOL);
        DEFAULT_LIST_ITEMS.put(PunishmentType.KICK, Material.YELLOW_WOOL);
        DEFAULT_LIST_ITEMS.put(PunishmentType.WARN, Material.LIME_WOOL);
    }

    private static final Material DEFAULT_FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int DEFAULT_PAGE_SIZE = 45;
    private static final int MAX_PAGE_SIZE = 45;

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

    public Material getPunishmentListItem(PunishmentType type) {
        Material fallback = DEFAULT_LIST_ITEMS.getOrDefault(type, Material.PAPER);
        return switch (type) {
            case BAN -> readMaterial("punishmentlist.item-ban", fallback);
            case IP_BAN -> readMaterial("punishmentlist.item-ban", fallback);
            case MUTE -> readMaterial("punishmentlist.item-mute", fallback);
            case KICK -> readMaterial("punishmentlist.item-kick", fallback);
            case WARN -> readMaterial("punishmentlist.item-warn", fallback);
        };
    }

    public Material getPunishmentListFiller() {
        return readMaterial("punishmentlist.filler-glass", DEFAULT_FILLER);
    }

    public int getPunishmentListPageSize() {
        int raw = config().getInt("punishmentlist.page-size", DEFAULT_PAGE_SIZE);
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, MAX_PAGE_SIZE);
    }

    private Material readMaterial(String key, Material fallback) {
        String raw = config().getString(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material match = Material.matchMaterial(raw.trim());
        if (match == null) {
            MurphyBan.getInstance().getLogger().log(Level.WARNING,
                    "Invalid material '" + raw + "' at " + key + " in config.yml; falling back to "
                            + fallback.name() + ".");
            return fallback;
        }
        return match;
    }
}