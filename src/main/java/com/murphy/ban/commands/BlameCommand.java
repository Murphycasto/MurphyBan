package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.util.BanLogger;
import com.murphy.ban.util.PunishmentFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlameCommand extends BaseCommand {

    private static final String COMMAND_NAME = "blame";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy HH:mm");
    private static final String DIVIDER = "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>";
    private static final int MAX_ENTRIES = 5;

    public BlameCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/blame <player>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.history")) {
            return;
        }
        String playerName = args[0];
        UUID uuid = resolveUUID(playerName);

        MurphyBan.getDatabase().getHistory(uuid)
                .thenAccept(history -> {
                    if (history.isEmpty()) {
                        sendMessage(sender, "no-history", Map.of("player", playerName));
                        return;
                    }
                    List<Punishment> recent = history.size() > MAX_ENTRIES
                            ? history.subList(0, MAX_ENTRIES)
                            : history;
                    Audience audience = plugin.getAudiences().sender(sender);
                    audience.sendMessage(MM.deserialize(DIVIDER));
                    audience.sendMessage(MM.deserialize(
                            "<white>" + escape(playerName) + "</white> <gray>— Last "
                                    + recent.size() + " punishments</gray>"));
                    for (Punishment p : recent) {
                        String date = DATE_FMT.format(new Date(p.issuedAt()));
                        audience.sendMessage(MM.deserialize(
                                "<gray>[" + p.type().name() + "]</gray> "
                                        + "<white>" + escape(p.reason()) + "</white> "
                                        + "<gray>by <white>" + escape(p.issuedBy()) + "</white> — "
                                        + date + "</gray>"));
                    }
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
