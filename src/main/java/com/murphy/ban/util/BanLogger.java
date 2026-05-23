package com.murphy.ban.util;

import com.murphy.ban.ConfigManager;
import com.murphy.ban.MurphyBan;

public final class BanLogger {

    private BanLogger() {
    }

    public static void debug(String message) {
        MurphyBan plugin = MurphyBan.getInstance();
        if (plugin == null) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        if (config == null || !config.isDebugMode()) {
            return;
        }
        plugin.getLogger().info("[DEBUG] " + message);
    }
}