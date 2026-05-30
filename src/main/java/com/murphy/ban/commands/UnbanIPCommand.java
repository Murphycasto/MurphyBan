package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.util.BanLogger;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UnbanIPCommand extends BaseCommand {

    private static final String COMMAND_NAME = "unbanip";

    public UnbanIPCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/unbanip <player|ip>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.ipban")) {
            return;
        }

        String input = args[0];
        String issuer = issuerName(sender);

        if (looksLikeIP(input)) {
            BanLogger.debug("/" + COMMAND_NAME + " treating input as raw IP: " + input);
            plugin.getPunishmentService().unIPBan(input, issuer)
                    .thenAccept(removed -> respond(sender, input, removed))
                    .exceptionally(ex -> handleFailure(sender, ex));
            return;
        }

        String playerName = input;
        UUID uuid = resolveUUID(playerName);
        BanLogger.debug("/" + COMMAND_NAME + " resolving IPs for player: " + playerName);

        MurphyBan.getDatabase().getKnownIPs(uuid)
                .thenCompose(ips -> tryUnbanFirst(ips, 0, issuer))
                .thenAccept(removed -> respond(sender, playerName, removed))
                .exceptionally(ex -> handleFailure(sender, ex));
    }

    private CompletableFuture<Boolean> tryUnbanFirst(List<String> ips, int index, String issuer) {
        if (index >= ips.size()) {
            return CompletableFuture.completedFuture(false);
        }
        return plugin.getPunishmentService().unIPBan(ips.get(index), issuer)
                .thenCompose(removed -> removed
                        ? CompletableFuture.completedFuture(true)
                        : tryUnbanFirst(ips, index + 1, issuer));
    }

    private void respond(CommandSender sender, String target, boolean removed) {
        if (removed) {
            sendMessage(sender, "unbanip-success", Map.of("target", target));
        } else {
            sendMessage(sender, "not-ipbanned", Map.of("target", target));
        }
    }

    private Void handleFailure(CommandSender sender, Throwable ex) {
        MurphyBan.getInstance().getLogger().severe(
                "[MurphyBan] Error executing /" + COMMAND_NAME + " for "
                        + sender.getName() + ": " + ex.getMessage());
        ex.printStackTrace();
        sendMessage(sender, "generic-error", Map.of());
        return null;
    }

    private boolean looksLikeIP(String s) {
        return s.contains(".") && !s.contains(" ");
    }

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return List.of();
    }
}