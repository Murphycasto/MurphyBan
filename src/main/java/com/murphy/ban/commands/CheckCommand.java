package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.BanLogger;
import com.murphy.ban.util.PunishmentFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckCommand extends BaseCommand {

    private static final String COMMAND_NAME = "check";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DIVIDER = "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>";

    public CheckCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/check <player>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.history")) {
            return;
        }
        Optional<OfflinePlayer> targetOpt = resolvePlayer(sender, args[0]);
        if (targetOpt.isEmpty()) {
            return;
        }
        OfflinePlayer target = targetOpt.get();
        String playerName = target.getName() != null ? target.getName() : args[0];
        DatabaseManager db = MurphyBan.getDatabase();

        CompletableFuture<Optional<Punishment>> banFuture =
                db.getActivePunishment(target.getUniqueId(), PunishmentType.BAN);
        CompletableFuture<Optional<Punishment>> muteFuture =
                db.getActivePunishment(target.getUniqueId(), PunishmentType.MUTE);
        CompletableFuture<List<Punishment>> historyFuture = db.getHistory(target.getUniqueId());

        CompletableFuture.allOf(banFuture, muteFuture, historyFuture)
                .thenRun(() -> {
                    Optional<Punishment> ban = banFuture.join().filter(p -> !p.isExpired());
                    Optional<Punishment> mute = muteFuture.join().filter(p -> !p.isExpired());
                    List<Punishment> activeWarns = historyFuture.join().stream()
                            .filter(p -> p.type() == PunishmentType.WARN && p.active() && !p.isExpired())
                            .toList();
                    long expireAfter = plugin.getConfigManager().getWarnExpireAfter();
                    long counted;
                    long aged;
                    if (expireAfter <= 0L) {
                        counted = activeWarns.size();
                        aged = 0L;
                    } else {
                        long cutoff = System.currentTimeMillis() - expireAfter;
                        counted = activeWarns.stream().filter(w -> w.issuedAt() >= cutoff).count();
                        aged = activeWarns.size() - counted;
                    }
                    Audience audience = plugin.getAudiences().sender(sender);
                    audience.sendMessage(MM.deserialize(DIVIDER));
                    audience.sendMessage(MM.deserialize(
                            "<white>" + escape(playerName) + "</white> <gray>— Status Check</gray>"));
                    audience.sendMessage(buildLine("Ban", ban));
                    audience.sendMessage(buildLine("Mute", mute));
                    audience.sendMessage(MM.deserialize(buildWarnLine(counted, aged)));
                    audience.sendMessage(MM.deserialize(DIVIDER));
                })
                .exceptionally(ex -> {
                    MurphyBan.getInstance().getLogger().severe(
                            "[MurphyBan] Error executing /" + COMMAND_NAME + " for "
                                    + sender.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    sendMessage(sender, "generic-error", Map.of());
                    return null;
                });
    }

    private String buildWarnLine(long counted, long aged) {
        if (aged == 0L) {
            return "<gray>Warnings: <yellow>" + counted + "</yellow></gray>";
        }
        return "<gray>Warnings: <yellow>" + counted + "</yellow> active "
                + "<dark_gray>(" + aged + " aged out)</dark_gray></gray>";
    }

    private Component buildLine(String label, Optional<Punishment> punishment) {
        if (punishment.isEmpty()) {
            return MM.deserialize("<gray>" + label + ": <green>None</green></gray>");
        }
        Punishment p = punishment.get();
        String activeWord = label.equals("Ban") ? "Banned" : "Muted";
        return MM.deserialize(
                "<gray>" + label + ": <red>" + activeWord + "</red> — "
                        + "<white>" + escape(p.reason()) + "</white> "
                        + "<gray>(expires <white>" + p.getFormattedExpiry() + "</white>)</gray>");
    }

    private String escape(String s) {
        return PunishmentFormatter.sanitize(s).replace("<", "\\<");
    }

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return List.of();
    }
}
