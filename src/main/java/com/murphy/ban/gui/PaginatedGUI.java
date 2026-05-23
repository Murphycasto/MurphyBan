package com.murphy.ban.gui;

import com.murphy.ban.MurphyBan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PaginatedGUI implements Listener {

    protected static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    protected final MurphyBan plugin;
    protected final Player viewer;
    protected final String titleTemplate;
    protected final int rowsPerPage;

    private int page;
    private Inventory inventory;
    private int contentSlots;
    private int prevSlot;
    private int infoSlot;
    private int nextSlot;

    protected PaginatedGUI(MurphyBan plugin, Player viewer, String titleTemplate, int rowsPerPage) {
        if (rowsPerPage < 2 || rowsPerPage > 6) {
            throw new IllegalArgumentException("rowsPerPage must be between 2 and 6");
        }
        this.plugin = plugin;
        this.viewer = viewer;
        this.titleTemplate = titleTemplate;
        this.rowsPerPage = rowsPerPage;
        this.contentSlots = rowsPerPage * 9 - 9;
        this.prevSlot = rowsPerPage * 9 - 9;
        this.infoSlot = rowsPerPage * 9 - 5;
        this.nextSlot = rowsPerPage * 9 - 1;
    }

    protected abstract List<ItemStack> getItems();

    protected abstract void onItemClick(InventoryClickEvent event, int slot, int page);

    public final void open() {
        String legacyTitle = LEGACY.serialize(MM.deserialize(titleTemplate));
        this.inventory = Bukkit.createInventory(null, rowsPerPage * 9, legacyTitle);
        buildPage();
        viewer.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void buildPage() {
        inventory.clear();
        List<ItemStack> all = getItems();
        int totalPages = totalPages(all.size());
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        int start = page * contentSlots;
        int end = Math.min(start + contentSlots, all.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, all.get(i));
        }
        inventory.setItem(prevSlot, navItem(Material.ARROW,
                page > 0 ? "<gray>← Previous Page</gray>" : "<dark_gray>← No previous page</dark_gray>",
                Collections.emptyList()));
        inventory.setItem(infoSlot, navItem(Material.GRAY_STAINED_GLASS_PANE,
                "<gray>Page <white>" + (page + 1) + "</white>/<white>" + Math.max(totalPages, 1) + "</white>",
                List.of("<gray>" + all.size() + " entries</gray>")));
        inventory.setItem(nextSlot, navItem(Material.ARROW,
                page < totalPages - 1 ? "<gray>Next Page →</gray>" : "<dark_gray>No next page →</dark_gray>",
                Collections.emptyList()));
    }

    private int totalPages(int itemCount) {
        if (itemCount == 0) {
            return 1;
        }
        return (int) Math.ceil(itemCount / (double) contentSlots);
    }

    private ItemStack navItem(Material material, String nameMM, List<String> loreMM) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LEGACY.serialize(MM.deserialize(nameMM)));
            if (!loreMM.isEmpty()) {
                List<String> lore = new ArrayList<>(loreMM.size());
                for (String line : loreMM) {
                    lore.add(LEGACY.serialize(MM.deserialize(line)));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public final void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        // Read-only GUI: cancel every click in the open view, including shift-clicks
        // from the player inventory that would otherwise move items into the GUI.
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= rowsPerPage * 9) {
            return;
        }
        if (slot == prevSlot) {
            if (page > 0) {
                page--;
                buildPage();
            }
            return;
        }
        if (slot == nextSlot) {
            if (page < totalPages(getItems().size()) - 1) {
                page++;
                buildPage();
            }
            return;
        }
        if (slot == infoSlot) {
            return;
        }
        if (slot < contentSlots) {
            onItemClick(event, slot, page);
        }
    }

    @EventHandler
    public final void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    protected final int getPage() {
        return page;
    }

    protected final int getContentSlots() {
        return contentSlots;
    }
}
