package com.murphy.ban.gui;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.database.DatabaseManager;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.PunishmentFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PunishmentListGUI extends PaginatedGUI {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy HH:mm");

    private static final int SLOT_PREV = 45;
    private static final int SLOT_HOPPER = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_BOOK = 50;
    private static final int SLOT_NEXT = 53;
    private static final int[] FILLER_SLOTS = {46, 47, 51, 52};

    public enum CategoryFilter {
        BAN, MUTE, KICK, WARN, ALL;

        public CategoryFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String displayName() {
            return switch (this) {
                case BAN -> "Ban";
                case MUTE -> "Mute";
                case KICK -> "Kick";
                case WARN -> "Warn";
                case ALL -> "All";
            };
        }
    }

    public enum StatusFilter {
        ACTIVE, INACTIVE, ALL;

        public StatusFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String displayName() {
            return switch (this) {
                case ACTIVE -> "Active";
                case INACTIVE -> "Inactive";
                case ALL -> "All";
            };
        }
    }

    private CategoryFilter category;
    private StatusFilter status;
    private final List<ItemStack> items;

    public PunishmentListGUI(MurphyBan plugin, Player viewer, List<Punishment> data,
                             CategoryFilter category, StatusFilter status, int page) {
        super(plugin, viewer, buildTitle(category, status), 6);
        this.category = category;
        this.status = status;
        this.items = buildItems(data, plugin);
        setPage(page);
    }

    private static String buildTitle(CategoryFilter category, StatusFilter status) {
        return "<gray>Punishments — <white>" + category.displayName()
                + " <gray>| <white>" + status.displayName();
    }

    @Override
    protected List<ItemStack> getItems() {
        return items;
    }

    @Override
    protected int getContentSlotCount() {
        return Math.min(45, plugin.getConfigManager().getPunishmentListPageSize());
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int slot, int page) {
        // Punishment items are informational; no click action.
    }

    @Override
    protected void renderControls(int totalPages, int itemCount) {
        Material filler = plugin.getConfigManager().getPunishmentListFiller();
        ItemStack fillerItem = filler(filler);
        for (int slot : FILLER_SLOTS) {
            getInventory().setItem(slot, fillerItem);
        }

        int currentPage = getPage();
        if (currentPage > 0) {
            getInventory().setItem(SLOT_PREV, navItem(Material.ARROW,
                    "<gray>← Previous", List.of("<dark_gray>Click to go back")));
        } else {
            getInventory().setItem(SLOT_PREV, fillerItem);
        }

        if (currentPage < totalPages - 1) {
            getInventory().setItem(SLOT_NEXT, navItem(Material.ARROW,
                    "<gray>Next →", List.of("<dark_gray>Click to advance")));
        } else {
            getInventory().setItem(SLOT_NEXT, fillerItem);
        }

        getInventory().setItem(SLOT_HOPPER, navItem(Material.HOPPER,
                "<white>Filter: <green>" + status.displayName(),
                buildStatusLore(status)));

        getInventory().setItem(SLOT_INFO, navItem(Material.PAPER,
                "<white>Page <green>" + (currentPage + 1) + "<white>/<green>" + Math.max(totalPages, 1),
                List.of("<gray>" + itemCount + " total punishment(s)")));

        getInventory().setItem(SLOT_BOOK, navItem(Material.BOOK,
                "<white>Category: <green>" + category.displayName(),
                buildCategoryLore(category)));
    }

    private static List<String> buildStatusLore(StatusFilter current) {
        List<String> lore = new ArrayList<>(StatusFilter.values().length);
        for (StatusFilter s : StatusFilter.values()) {
            String color = s == current ? "<yellow>" : "<dark_gray>";
            lore.add(color + "● " + s.displayName());
        }
        return lore;
    }

    private static List<String> buildCategoryLore(CategoryFilter current) {
        List<String> lore = new ArrayList<>(CategoryFilter.values().length);
        for (CategoryFilter c : CategoryFilter.values()) {
            String color = c == current ? "<yellow>" : "<dark_gray>";
            lore.add(color + "● " + c.displayName());
        }
        return lore;
    }

    @Override
    protected boolean onControlClick(InventoryClickEvent event, int slot, int page, int totalPages) {
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    setPage(page - 1);
                    rebuild();
                }
                return true;
            }
            case SLOT_NEXT -> {
                if (page < totalPages - 1) {
                    setPage(page + 1);
                    rebuild();
                }
                return true;
            }
            case SLOT_HOPPER -> {
                cycleStatus();
                return true;
            }
            case SLOT_BOOK -> {
                cycleCategory();
                return true;
            }
            case SLOT_INFO -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void cycleStatus() {
        this.status = status.next();
        refetchAndReopen();
    }

    private void cycleCategory() {
        this.category = category.next();
        refetchAndReopen();
    }

    private void refetchAndReopen() {
        final Player target = viewer;
        final CategoryFilter c = this.category;
        final StatusFilter s = this.status;
        fetch(plugin, c, s).thenAccept(data ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        new PunishmentListGUI(plugin, target, data, c, s, 0).open()))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "[MurphyBan] Failed to refetch punishment list", ex);
                    return null;
                });
    }

    public static CompletableFuture<List<Punishment>> fetch(MurphyBan plugin,
                                                             CategoryFilter category,
                                                             StatusFilter status) {
        DatabaseManager db = MurphyBan.getDatabase();
        CompletableFuture<List<Punishment>> future;
        if (category == CategoryFilter.ALL) {
            future = switch (status) {
                case ACTIVE -> db.getActivePunishmentsAllTypes();
                case INACTIVE -> db.getInactivePunishmentsAllTypes();
                case ALL -> db.getAllPunishmentsAllTypes();
            };
        } else {
            PunishmentType type = toType(category);
            future = switch (status) {
                case ACTIVE -> db.getActivePunishments(type);
                case INACTIVE -> db.getInactivePunishments(type);
                case ALL -> db.getAllPunishments(type);
            };
        }
        if (status == StatusFilter.INACTIVE) {
            future = future.thenApply(PunishmentListGUI::removeKicks);
        }
        return future;
    }

    private static List<Punishment> removeKicks(List<Punishment> list) {
        List<Punishment> filtered = new ArrayList<>(list.size());
        for (Punishment p : list) {
            if (p.type() != PunishmentType.KICK) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private static PunishmentType toType(CategoryFilter category) {
        return switch (category) {
            case BAN -> PunishmentType.BAN;
            case MUTE -> PunishmentType.MUTE;
            case KICK -> PunishmentType.KICK;
            case WARN -> PunishmentType.WARN;
            case ALL -> throw new IllegalArgumentException("ALL has no single PunishmentType");
        };
    }

    private static List<ItemStack> buildItems(List<Punishment> data, MurphyBan plugin) {
        List<ItemStack> result = new ArrayList<>(data.size());
        for (Punishment p : data) {
            result.add(toItem(p, plugin));
        }
        return result;
    }

    private static ItemStack toItem(Punishment p, MurphyBan plugin) {
        Material material = plugin.getConfigManager().getPunishmentListItem(p.type());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(LEGACY.serialize(MM.deserialize(
                "<white>" + p.type().getDisplayName() + " — " + escape(p.playerName()))));

        List<String> lore = new ArrayList<>();
        lore.add(line("<gray>Status: " + PunishmentFormatter.resolveStatus(p)));
        lore.add(line("<gray>Reason: <white>" + escape(p.reason())));
        lore.add(line("<gray>Issued by: <white>" + escape(p.issuedBy())));
        lore.add(line("<gray>Issued: <white>" + DATE_FMT.format(new Date(p.issuedAt()))));
        lore.add(line("<gray>Duration: <white>" + p.getFormattedDuration()));
        lore.add(line("<gray>Expires: <white>" + p.getFormattedExpiry()));
        lore.add(line("<gray>ID: <white>#" + p.id()));
        if (p.isRevoked()) {
            lore.add(line("<gray>Revoked by: <white>" + escape(p.revokedBy())));
            String revokedAtText = p.revokedAt() > 0L
                    ? DATE_FMT.format(new Date(p.revokedAt()))
                    : "Unknown";
            lore.add(line("<gray>Revoked at: <white>" + revokedAtText));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String line(String mm) {
        return LEGACY.serialize(MM.deserialize(mm));
    }

    private static String escape(String s) {
        return PunishmentFormatter.sanitize(s).replace("<", "\\<");
    }
}
