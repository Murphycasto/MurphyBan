package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.BanLogger;
import com.murphy.ban.util.PunishmentFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UnwarnCommand extends BaseCommand {

    private static final String COMMAND_NAME = "unwarn";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public UnwarnCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/unwarn <player> [id]")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.unwarn")) {
            return;
        }
        String playerName = args[0];
        UUID uuid = resolveUUID(playerName);

        if (args.length == 1) {
            listActiveWarns(sender, uuid, playerName);
            return;
        }

        int warnId;
        try {
            warnId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sendMessage(sender, "warn-not-found", Map.of("player", playerName, "id", args[1]));
            return;
        }

        String issuer = issuerName(sender);
        MurphyBan.getDatabase().getHistory(uuid)
                .thenCompose(history -> {
                    Optional<Punishment> match = history.stream()
                            .filter(p -> p.id() == warnId
                                    && p.type() == PunishmentType.WARN
                                    && p.active()
                                    && !p.isExpired())
                            .findFirst();
                    if (match.isEmpty()) {
                        sendMessage(sender, "warn-not-found", Map.of(
                                "player", playerName,
                                "id", String.valueOf(warnId)));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return plugin.getPunishmentService().unwarn(warnId, issuer)
                            .thenRun(() -> sendMessage(sender, "unwarn-success", Map.of(
                                    "player", playerName,
                                    "id", String.valueOf(warnId))));
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

    private void listActiveWarns(CommandSender sender, UUID uuid, String playerName) {
        MurphyBan.getDatabase().getHistory(uuid)
                .thenAccept(history -> {
                    List<Punishment> warns = history.stream()
                            .filter(p -> p.type() == PunishmentType.WARN && p.active() && !p.isExpired())
                            .toList();
                    Audience audience = plugin.getAudiences().sender(sender);
                    if (warns.isEmpty()) {
                        sendMessage(sender, "warn-not-found", Map.of(
                                "player", playerName,
                                "id", "*"));
                        return;
                    }
                    audience.sendMessage(MM.deserialize(
                            "<gray>Active warnings for <white>" + playerName + "</white>:"));
                    for (Punishment w : warns) {
                        Component line = MM.deserialize(
                                "<gray>  #<yellow>" + w.id() + "</yellow> — "
                                        + "<white>" + escape(w.reason()) + "</white> "
                                        + "<dark_gray>(by " + escape(w.issuedBy()) + ")</dark_gray>");
                        audience.sendMessage(line);
                    }
                })
                .exceptionally(ex -> {
                    MurphyBan.getInstance().getLogger().severe(
                            "[MurphyBan] Error executing /" + COMMAND_NAME + " (list) for "
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
