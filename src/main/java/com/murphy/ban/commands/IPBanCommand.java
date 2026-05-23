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
import java.util.regex.Pattern;

public class IPBanCommand extends BaseCommand {

    private static final String COMMAND_NAME = "ipban";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final List<String> DURATION_SUGGESTIONS = List.of("1h", "1d", "7d", "30d", "permanent");

    public IPBanCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/ipban <player|ip> [duration] [reason]")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.ipban")) {
            return;
        }
        long expiresAt = -1L;
        int reasonStart = 1;
        if (args.length >= 2) {
            try {
                long parsed = DurationParser.parse(args[1]);
                expiresAt = parsed < 0L ? -1L : System.currentTimeMillis() + parsed;
                reasonStart = 2;
            } catch (IllegalArgumentException ignored) {
            }
        }
        String reason = reasonStart < args.length ? joinReason(args, reasonStart) : defaultReason();
        String issuer = issuerName(sender);
        long expiresFinal = expiresAt;
        String durationText = expiresAt < 0L
                ? "permanently"
                : "for " + DurationParser.format(expiresAt - System.currentTimeMillis());

        if (isIPAddress(args[0])) {
            String ip = args[0];
            BanLogger.debug("/" + COMMAND_NAME + " issuing by raw IP: ip=" + ip
                    + " expiresAt=" + expiresFinal + " issuer=" + issuer);
            plugin.getPunishmentService().ipBanByAddress(ip, reason, expiresFinal, issuer)
                    .thenRun(() -> sendMessage(sender, "ipban-success", Map.of(
                            "target", ip,
                            "duration", durationText)))
                    .exceptionally(ex -> {
                        MurphyBan.getInstance().getLogger().severe(
                                "[MurphyBan] Error executing /" + COMMAND_NAME + " for "
                                        + sender.getName() + ": " + ex.getMessage());
                        ex.printStackTrace();
                        sendMessage(sender, "generic-error", Map.of());
                        return null;
                    });
            return;
        }

        Optional<OfflinePlayer> targetOpt = resolvePlayer(sender, args[0]);
        if (targetOpt.isEmpty()) {
            return;
        }
        OfflinePlayer target = targetOpt.get();
        String playerName = target.getName() != null ? target.getName() : args[0];
        BanLogger.debug("/" + COMMAND_NAME + " issuing by player: target=" + playerName
                + " expiresAt=" + expiresFinal + " issuer=" + issuer);
        plugin.getPunishmentService().ipBan(target, reason, expiresFinal, issuer)
                .thenRun(() -> sendMessage(sender, "ipban-success", Map.of(
                        "target", playerName,
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

    private boolean isIPAddress(String s) {
        return IPV4.matcher(s).matches() || (s.contains(":") && !s.contains(" "));
    }
}