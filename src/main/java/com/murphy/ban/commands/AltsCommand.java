package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.gui.AltsGUI;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.BanLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AltsCommand extends BaseCommand {

    private static final String COMMAND_NAME = "alts";

    public AltsCommand(MurphyBan plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requireArgs(sender, args, 1, "/alts <player>")) {
            return;
        }
        if (!requirePermission(sender, "murphyban.alts")) {
            return;
        }
        if (!requirePlayer(sender)) {
            return;
        }
        Optional<OfflinePlayer> targetOpt = resolvePlayer(sender, args[0]);
        if (targetOpt.isEmpty()) {
            return;
        }
        OfflinePlayer target = targetOpt.get();
        UUID targetUuid = target.getUniqueId();
        String playerName = target.getName() != null ? target.getName() : args[0];
        Player viewer = (Player) sender;
        DatabaseManager db = MurphyBan.getDatabase();

        db.getKnownIPs(targetUuid)
                .thenCompose(ips -> collectAccounts(db, ips, targetUuid))
                .thenCompose(accounts -> resolveBanStatuses(db, accounts)
                        .thenAccept(banned -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (accounts.isEmpty()) {
                                sendMessage(sender, "no-alts", Map.of("player", playerName));
                                return;
                            }
                            new AltsGUI(plugin, viewer, playerName, accounts, banned).open();
                        })))
                .exceptionally(ex -> {
                    MurphyBan.getInstance().getLogger().severe(
                            "[MurphyBan] Error executing /" + COMMAND_NAME + " for "
                                    + sender.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    sendMessage(sender, "generic-error", Map.of());
                    return null;
                });
    }

    private CompletableFuture<List<UUID>> collectAccounts(DatabaseManager db, List<String> ips, UUID self) {
        if (ips.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<List<UUID>>> lookups = new ArrayList<>(ips.size());
        for (String ip : ips) {
            lookups.add(db.getAccountsOnIP(ip));
        }
        return CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    LinkedHashSet<UUID> merged = new LinkedHashSet<>();
                    for (CompletableFuture<List<UUID>> f : lookups) {
                        for (UUID u : f.join()) {
                            if (!u.equals(self)) {
                                merged.add(u);
                            }
                        }
                    }
                    return new ArrayList<>(merged);
                });
    }

    private CompletableFuture<Map<UUID, Boolean>> resolveBanStatuses(DatabaseManager db, List<UUID> accounts) {
        ConcurrentHashMap<UUID, Boolean> map = new ConcurrentHashMap<>();
        if (accounts.isEmpty()) {
            return CompletableFuture.completedFuture(map);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>(accounts.size());
        for (UUID uuid : new HashSet<>(accounts)) {
            futures.add(db.getActivePunishment(uuid, PunishmentType.BAN)
                    .thenAccept(opt -> map.put(uuid, opt.isPresent() && !opt.get().isExpired())));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> map);
    }

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return List.of();
    }
}
