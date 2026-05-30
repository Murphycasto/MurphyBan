package com.murphy.ban;

import com.murphy.ban.commands.AltsCommand;
import com.murphy.ban.commands.BanCommand;
import com.murphy.ban.commands.BanListCommand;
import com.murphy.ban.commands.BlameCommand;
import com.murphy.ban.commands.CheckCommand;
import com.murphy.ban.commands.HistoryCommand;
import com.murphy.ban.commands.IPBanCommand;
import com.murphy.ban.commands.KickCommand;
import com.murphy.ban.commands.MurphyBanCommand;
import com.murphy.ban.commands.MuteCommand;
import com.murphy.ban.commands.MuteListCommand;
import com.murphy.ban.commands.PunishmentListCommand;
import com.murphy.ban.commands.StaffHistoryCommand;
import com.murphy.ban.commands.UnbanCommand;
import com.murphy.ban.commands.UnbanIPCommand;
import com.murphy.ban.commands.UnmuteCommand;
import com.murphy.ban.commands.UnwarnCommand;
import com.murphy.ban.commands.WarnCommand;
import com.murphy.ban.database.DatabaseFactory;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.listeners.PlayerChatListener;
import com.murphy.ban.listeners.PlayerLoginListener;
import com.murphy.ban.manager.MuteCache;
import com.murphy.ban.manager.PunishmentService;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.placeholders.MurphyBanExpansion;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

public final class MurphyBan extends JavaPlugin {

    private static final long EXPIRY_SWEEP_TICKS = 20L * 60L * 5L;

    private static MurphyBan instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager database;
    private BukkitAudiences audiences;
    private MuteCache muteCache;
    private PunishmentService punishmentService;
    private MurphyBanExpansion papiExpansion;
    private BukkitTask expirySweeper;

    public static MurphyBan getInstance() {
        return instance;
    }

    public static DatabaseManager getDatabase() {
        return instance == null ? null : instance.database;
    }

    @Override
    public void onEnable() {
        instance = this;
        logBanner();

        saveDefaultConfig();
        this.configManager = new ConfigManager();
        if (!validateConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.messageManager = new MessageManager(this);
        this.audiences = BukkitAudiences.create(this);
        this.muteCache = new MuteCache();

        getCommand("murphyban").setExecutor(new MurphyBanCommand(this));

        this.database = DatabaseFactory.create(configManager);
        this.punishmentService = new PunishmentService(this, database, muteCache, configManager, messageManager);

        database.connect()
                .thenCompose(v -> database.createTables())
                .thenRun(() -> getServer().getScheduler().runTask(this, () -> {
                    registerListeners();
                    registerCommands();
                    registerPlaceholders();
                    startExpirySweeper();
                }))
                .exceptionally(ex -> {
                    getLogger().log(Level.SEVERE, "Failed to initialise database; disabling plugin.", ex);
                    getServer().getScheduler().runTask(this, () ->
                            getServer().getPluginManager().disablePlugin(this));
                    return null;
                });

        getLogger().info("MurphyBan v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        expirySweeper = null;

        if (papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (Throwable ex) {
                getLogger().log(Level.WARNING, "Error while unregistering PlaceholderAPI expansion.", ex);
            }
            papiExpansion = null;
        }

        if (muteCache != null) {
            muteCache.clear();
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        if (database != null) {
            try {
                database.disconnect().join();
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "Error while closing database connection pool.", ex);
            }
        }
        getLogger().info("[MurphyBan] Disabled successfully.");
        instance = null;
    }

    private void logBanner() {
        String version = getDescription().getVersion();
        String versionLine = "║   Version: " + pad(version, 17) + " ║";
        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║         MurphyBan            ║");
        getLogger().info("║   Punishment Management      ║");
        getLogger().info(versionLine);
        getLogger().info("║   Author:  Murphycasto       ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getLogger().info("[MurphyBan] Database connected and ready.");
    }

    private void registerCommands() {
        bind("ban", new BanCommand(this));
        bind("ipban", new IPBanCommand(this));
        bind("mute", new MuteCommand(this));
        bind("kick", new KickCommand(this));
        bind("warn", new WarnCommand(this));
        bind("unban", new UnbanCommand(this));
        bind("unbanip", new UnbanIPCommand(this));
        bind("unmute", new UnmuteCommand(this));
        bind("unwarn", new UnwarnCommand(this));
        bind("check", new CheckCommand(this));
        bind("history", new HistoryCommand(this));
        bind("blame", new BlameCommand(this));
        bind("staffhistory", new StaffHistoryCommand(this));
        bind("alts", new AltsCommand(this));
        bind("punishmentlist", new PunishmentListCommand(this));
        bind("banlist", new BanListCommand(this));
        bind("mutelist", new MuteListCommand(this));
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("[MurphyBan] PlaceholderAPI not found — placeholders disabled.");
            return;
        }
        try {
            this.papiExpansion = new MurphyBanExpansion(this);
            if (papiExpansion.register()) {
                getLogger().info("[MurphyBan] PlaceholderAPI expansion registered.");
            } else {
                getLogger().warning("[MurphyBan] PlaceholderAPI expansion failed to register.");
                papiExpansion = null;
            }
        } catch (Throwable ex) {
            getLogger().log(Level.WARNING, "[MurphyBan] PlaceholderAPI expansion threw during registration.", ex);
            papiExpansion = null;
        }
    }

    private void startExpirySweeper() {
        this.expirySweeper = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::sweepExpiredPunishments,
                EXPIRY_SWEEP_TICKS, EXPIRY_SWEEP_TICKS);
    }

    private void sweepExpiredPunishments() {
        database.getExpiredActivePunishments()
                .thenAccept(this::handleExpiredBatch)
                .exceptionally(ex -> {
                    getLogger().log(Level.WARNING, "[MurphyBan] Expiry sweep failed.", ex);
                    return null;
                });
        sweepAgeExpiredWarns();
    }

    private void handleExpiredBatch(List<Punishment> expired) {
        if (expired.isEmpty()) {
            return;
        }
        for (Punishment p : expired) {
            database.expirePunishment(p.id()).exceptionally(ex -> {
                getLogger().log(Level.WARNING,
                        "[MurphyBan] Failed to expire punishment id=" + p.id(), ex);
                return null;
            });
            if (p.type() == PunishmentType.MUTE && muteCache != null) {
                muteCache.invalidate(p.uuid());
            }
        }
        getLogger().info("[MurphyBan] Expiry sweep flipped " + expired.size() + " punishment(s).");
    }

    private void sweepAgeExpiredWarns() {
        long expireAfter = configManager.getWarnExpireAfter();
        if (expireAfter <= 0L) {
            return;
        }
        long cutoff = System.currentTimeMillis() - expireAfter;
        database.getActiveWarnsBefore(cutoff)
                .thenAccept(aged -> {
                    if (aged.isEmpty()) {
                        return;
                    }
                    for (Punishment w : aged) {
                        database.expirePunishment(w.id()).exceptionally(ex -> {
                            getLogger().log(Level.WARNING,
                                    "[MurphyBan] Failed to age-expire warn id=" + w.id(), ex);
                            return null;
                        });
                    }
                    getLogger().info("[MurphyBan] Age-expired " + aged.size() + " warn(s).");
                })
                .exceptionally(ex -> {
                    getLogger().log(Level.WARNING, "[MurphyBan] Warn age-expiry sweep failed.", ex);
                    return null;
                });
    }

    private void bind(String name, com.murphy.ban.commands.BaseCommand handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().log(Level.WARNING, "Command '" + name + "' is not declared in plugin.yml; skipping registration.");
            return;
        }
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    private boolean validateConfig() {
        String dbType = configManager.getDatabaseType();
        if (!dbType.equalsIgnoreCase("sqlite") && !dbType.equalsIgnoreCase("mysql")) {
            getLogger().log(Level.SEVERE, "Invalid database.type '" + dbType + "' in config.yml. Must be 'sqlite' or 'mysql'. Disabling plugin.");
            return false;
        }

        String autoPunish = configManager.getAutoPunishType();
        if (!autoPunish.equalsIgnoreCase("mute") && !autoPunish.equalsIgnoreCase("ban")) {
            getLogger().log(Level.WARNING, "Invalid warns.auto-punish '" + autoPunish + "' in config.yml. Must be 'mute' or 'ban'. Defaulting to 'mute'.");
            getConfig().set("warns.auto-punish", "mute");
        }

        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public BukkitAudiences getAudiences() {
        return audiences;
    }

    public MuteCache getMuteCache() {
        return muteCache;
    }

    public PunishmentService getPunishmentService() {
        return punishmentService;
    }
}
