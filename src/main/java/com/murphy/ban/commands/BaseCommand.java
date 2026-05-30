package com.murphy.ban.commands;

import com.murphy.ban.MessageManager;
import com.murphy.ban.MurphyBan;
import com.murphy.ban.util.BanLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseCommand implements TabExecutor {

    protected final MurphyBan plugin;

    protected BaseCommand(MurphyBan plugin) {
        this.plugin = plugin;
    }

    protected abstract void execute(CommandSender sender, String[] args);

    protected abstract List<String> suggest(CommandSender sender, String[] args);

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        execute(sender, args);
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = suggest(sender, args);
        return result == null ? Collections.emptyList() : result;
    }

    protected boolean requireArgs(CommandSender sender, String[] args, int minimum, String usage) {
        if (args.length >= minimum) {
            return true;
        }
        sendMessage(sender, "usage", Map.of("usage", usage.replace("<", "\\<")));
        return false;
    }

    protected boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sendMessage(sender, "no-permission", Map.of());
        return false;
    }

    protected boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sendMessage(sender, "must-be-player", Map.of());
        return false;
    }

    @SuppressWarnings("deprecation")
    protected Optional<OfflinePlayer> resolvePlayer(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId() == null) {
            plugin.getLogger().warning("[MurphyBan] resolvePlayer('" + name
                    + "') returned an OfflinePlayer with a null UUID — Bukkit/Mojang lookup likely failed.");
            sendMessage(sender, "player-not-found", Map.of("player", name));
            return Optional.empty();
        }
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            BanLogger.debug("resolvePlayer('" + name + "') → uuid=" + target.getUniqueId()
                    + " has never joined and is offline; treating as not found.");
            sendMessage(sender, "player-not-found", Map.of("player", name));
            return Optional.empty();
        }
        BanLogger.debug("resolvePlayer('" + name + "') → uuid=" + target.getUniqueId()
                + " online=" + target.isOnline() + " hasPlayedBefore=" + target.hasPlayedBefore());
        return Optional.of(target);
    }

    @SuppressWarnings("deprecation")
    protected UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    protected void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        MessageManager messages = plugin.getMessageManager();
        Component msg = messages.get(key, placeholders);
        plugin.getAudiences().sender(sender).sendMessage(msg);
    }

    protected List<String> suggestOnlinePlayers(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, names, matches);
        Collections.sort(matches);
        return matches;
    }

    protected List<String> suggestFromList(String prefix, List<String> options) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, options, matches);
        Collections.sort(matches);
        return matches;
    }

    protected String joinReason(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    protected String defaultReason() {
        String raw = plugin.getMessageManager().getRaw("default-reason");
        return raw.isEmpty() ? "No reason provided" : raw;
    }

    protected String issuerName(CommandSender sender) {
        return sender instanceof Player p ? p.getName() : "CONSOLE";
    }

    protected String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}