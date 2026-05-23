package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.util.BanLogger;
import com.murphy.ban.util.DurationParser;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BanCommand extends BaseCommand {

    private static final String COMMAND_NAME = "ban";
    private static final List<String> DURATION_SUGGESTIONS = List.of("1h", "1d", "7d", "30d", "permanent");

    public BanCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/ban <player> [duration] [reason]")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.ban")) {
            return;
        }
        Optional<OfflinePlayer> targetOpt = resolvePlayer(sender, args[0]);
        if (targetOpt.isEmpty()) {
            return;
        }
        OfflinePlayer target = targetOpt.get();

        long expiresAt = -1L;
        int reasonStart = 1;
        if (args.length >= 2) {
            try {
                long parsed = DurationParser.parse(args[1]);
                expiresAt = parsed < 0L ? -1L : System.currentTimeMillis() + parsed;
                reasonStart = 2;
            } catch (IllegalArgumentException ignored) {
                // first non-name arg isn't a duration — fall through and treat it as reason.
            }
        }

        String reason = reasonStart < args.length ? joinReason(args, reasonStart) : defaultReason();
        String issuer = issuerName(sender);
        String durationText = expiresAt < 0L
                ? "permanently"
                : "for " + DurationParser.format(expiresAt - System.currentTimeMillis());
        String playerName = target.getName() != null ? target.getName() : args[0];

        BanLogger.debug("/" + COMMAND_NAME + " issuing punishment: target=" + playerName
                + " expiresAt=" + expiresAt + " issuer=" + issuer);
        plugin.getPunishmentService().ban(target, reason, expiresAt, issuer)
                .thenRun(() -> sendMessage(sender, "ban-success", Map.of(
                        "player", playerName,
                        "duration", durationText)))
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
        if (args.length == 2) {
            return suggestFromList(args[1], DURATION_SUGGESTIONS);
        }
        return List.of();
    }
}