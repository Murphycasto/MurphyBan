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
    protected static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    protected final MurphyBan plugin;
    protected final Player viewer;
    protected final String titleTemplate;
    protected final int rowsPerPage;

    private int page;
    private Inventory inventory;
    private final int contentSlots;

    protected PaginatedGUI(MurphyBan plugin, Player viewer, String titleTemplate, int rowsPerPage) {
        if (rowsPerPage < 2 || rowsPerPage > 6) {
            throw new IllegalArgumentException("rowsPerPage must be between 2 and 6");
        }
        this.plugin = plugin;
        this.viewer = viewer;
        this.titleTemplate = titleTemplate;
        this.rowsPerPage = rowsPerPage;
        this.contentSlots = rowsPerPage * 9 - 9;
    }

    protected abstract List<ItemStack> getItems();

    protected abstract void onItemClick(InventoryClickEvent event, int slot, int page);

    protected int getContentSlotCount() {
        return contentSlots;
    }

    public final void open() {
        String legacyTitle = LEGACY.serialize(MM.deserialize(titleTemplate));
        this.inventory = Bukkit.createInventory(null, rowsPerPage * 9, legacyTitle);
        buildPage();
        viewer.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    protected final void rebuild() {
        if (inventory == null) {
            return;
        }
        buildPage();
    }

    private void buildPage() {
        inventory.clear();
        List<ItemStack> all = getItems();
        int slotCount = getContentSlotCount();
        int totalPages = totalPages(all.size());
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }
        int start = page * slotCount;
        int end = Math.min(start + slotCount, all.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, all.get(i));
        }
        renderControls(totalPages, all.size());
    }

    protected void renderControls(int totalPages, int itemCount) {
        int prevSlot = rowsPerPage * 9 - 9;
        int infoSlot = rowsPerPage * 9 - 5;
        int nextSlot = rowsPerPage * 9 - 1;
        inventory.setItem(prevSlot, navItem(Material.ARROW,
                page > 0 ? "<gray>← Previous Page</gray>" : "<dark_gray>← No previous page</dark_gray>",
                Collections.emptyList()));
        inventory.setItem(infoSlot, navItem(Material.GRAY_STAINED_GLASS_PANE,
                "<gray>Page <white>" + (page + 1) + "</white>/<white>" + Math.max(totalPages, 1) + "</white>",
                List.of("<gray>" + itemCount + " entries</gray>")));
        inventory.setItem(nextSlot, navItem(Material.ARROW,
                page < totalPages - 1 ? "<gray>Next Page →</gray>" : "<dark_gray>No next page →</dark_gray>",
                Collections.emptyList()));
    }

    protected boolean onControlClick(InventoryClickEvent event, int slot, int page, int totalPages) {
        int prevSlot = rowsPerPage * 9 - 9;
        int infoSlot = rowsPerPage * 9 - 5;
        int nextSlot = rowsPerPage * 9 - 1;
        if (slot == prevSlot) {
            if (page > 0) {
                this.page--;
                buildPage();
            }
            return true;
        }
        if (slot == nextSlot) {
            if (page < totalPages - 1) {
                this.page++;
                buildPage();
            }
            return true;
        }
        return slot == infoSlot;
    }

    private int totalPages(int itemCount) {
        int slotCount = getContentSlotCount();
        if (itemCount == 0 || slotCount <= 0) {
            return 1;
        }
        return (int) Math.ceil(itemCount / (double) slotCount);
    }

    protected ItemStack navItem(Material material, String nameMM, List<String> loreMM) {
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
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= rowsPerPage * 9) {
            return;
        }
        int slotCount = getContentSlotCount();
        int totalPages = totalPages(getItems().size());
        if (slot < slotCount) {
            onItemClick(event, slot, page);
            return;
        }
        onControlClick(event, slot, page, totalPages);
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

    protected final void setPage(int page) {
        this.page = Math.max(0, page);
    }

    protected final Inventory getInventory() {
        return inventory;
    }

    protected final int getContentSlots() {
        return contentSlots;
    }
}
