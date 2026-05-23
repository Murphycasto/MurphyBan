package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.util.BanLogger;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WarnCommand extends BaseCommand {

    private static final String COMMAND_NAME = "warn";

    public WarnCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/warn <player> [reason]")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.warn")) {
            return;
        }
        Optional<OfflinePlayer> targetOpt = resolvePlayer(sender, args[0]);
        if (targetOpt.isEmpty()) {
            return;
        }
        OfflinePlayer target = targetOpt.get();

        String reason = args.length >= 2 ? joinReason(args, 1) : defaultReason();
        String issuer = issuerName(sender);
        String playerName = target.getName() != null ? target.getName() : args[0];

        BanLogger.debug("/" + COMMAND_NAME + " issuing punishment: target=" + playerName
                + " issuer=" + issuer);
        plugin.getPunishmentService().warn(target, reason, issuer)
                .thenAccept(result -> {
                    sendMessage(sender, "warn-success", Map.of(
                            "player", playerName,
                            "count", String.valueOf(result.warnCount())));
                    if (result.autoPunished()) {
                        sendMessage(sender, "auto-punish-notify", Map.of(
                                "player", playerName,
                                "threshold", String.valueOf(plugin.getConfigManager().getWarnThreshold()),
                                "action", result.autoPunishAction()));
                    }
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

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return List.of();
    }
}