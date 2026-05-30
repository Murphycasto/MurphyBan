package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.gui.PunishmentListGUI;
import com.murphy.ban.gui.PunishmentListGUI.CategoryFilter;
import com.murphy.ban.gui.PunishmentListGUI.StatusFilter;
import com.murphy.ban.util.BanLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PunishmentListCommand extends BaseCommand {

    private static final String COMMAND_NAME = "punishmentlist";
    private static final List<String> CATEGORY_SUGGESTIONS = List.of("ban", "mute", "kick", "warn", "all");

    private final CategoryFilter forcedCategory;

    public PunishmentListCommand(MurphyBan plugin) {
        this(plugin, null);
    }

    public PunishmentListCommand(MurphyBan plugin, CategoryFilter forcedCategory) {
        super(plugin);
        this.forcedCategory = forcedCategory;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        BanLogger.debug("Executing /" + COMMAND_NAME + " by " + sender.getName()
                + " with args: " + Arrays.toString(args));
        if (!requirePermission(sender, "murphyban.history")) {
            return;
        }
        if (!requirePlayer(sender)) {
            return;
        }
        Player viewer = (Player) sender;
        CategoryFilter category = forcedCategory != null
                ? forcedCategory
                : parseCategory(args.length >= 1 ? args[0] : null);
        StatusFilter status = StatusFilter.ALL;

        PunishmentListGUI.fetch(plugin, category, status)
                .thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (data.isEmpty()) {
                        sendMessage(sender, "no-punishments", Map.of());
                        return;
                    }
                    new PunishmentListGUI(plugin, viewer, data, category, status, 0).open();
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

    @Override
    protected List<String> suggest(CommandSender sender, String[] args) {
        if (forcedCategory == null && args.length == 1) {
            return suggestFromList(args[0], CATEGORY_SUGGESTIONS);
        }
        return List.of();
    }

    private CategoryFilter parseCategory(String input) {
        if (input == null || input.isBlank()) {
            return CategoryFilter.ALL;
        }
        try {
            return CategoryFilter.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CategoryFilter.ALL;
        }
    }
}
