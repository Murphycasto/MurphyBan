package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.util.BanLogger;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnbanCommand extends BaseCommand {

    private static final String COMMAND_NAME = "unban";

    public UnbanCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/unban <player>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.unban")) {
            return;
        }
        String playerName = args[0];
        UUID uuid = resolveUUID(playerName);
        String issuer = issuerName(sender);

        plugin.getPunishmentService().unban(uuid, issuer)
                .thenAccept(removed -> {
                    if (removed) {
                        sendMessage(sender, "unban-success", Map.of("player", playerName));
                    } else {
                        sendMessage(sender, "not-banned", Map.of("player", playerName));
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
