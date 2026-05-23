package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.gui.HistoryGUI;
import com.murphy.ban.util.BanLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class StaffHistoryCommand extends BaseCommand {

    private static final String COMMAND_NAME = "staffhistory";

    public StaffHistoryCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/staffhistory <staff>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.history")) {
            return;
        }
        if (!requirePlayer(sender)) {
            return;
        }
        String staffName = args[0];
        Player viewer = (Player) sender;

        MurphyBan.getDatabase().getStaffHistory(staffName)
                .thenAccept(history -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (history.isEmpty()) {
                        sendMessage(sender, "no-staff-history", Map.of("staff", staffName));
                        return;
                    }
                    String title = "<gray>Staff History — <white>" + escape(staffName) + "</white>";
                    new HistoryGUI(plugin, viewer, title, history).open();
                }))
                .exceptionally(ex -> {
                    MurphyBan.getInstance().getLogger().severe(
                            "[MurphyBan] Error executing /" + COMMAND_NAME + " for "
                                    + sender.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    sendMessage(sender, "generic-error", Map.of());
                    return null;
                });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("<", "\\<");
    }

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return List.of();
    }
}
