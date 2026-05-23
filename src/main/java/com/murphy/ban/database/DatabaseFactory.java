package com.murphy.ban.database;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MurphyBan;

public final class DatabaseFactory {

    private DatabaseFactory() {
    }

    public static DatabaseManager create(ConfigManager config) {
        MurphyBan plugin = MurphyBan.getInstance();
        return switch (config.getDatabaseType().toLowerCase()) {
            case "mysql" -> new MySQLDatabase(plugin);
            default -> new SQLiteDatabase(plugin);
        };
    }
}