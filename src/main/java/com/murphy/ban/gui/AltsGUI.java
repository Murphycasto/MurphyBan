package com.murphy.ban.gui;

import com.murphy.ban.MurphyBan;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AltsGUI extends PaginatedGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy HH:mm");

    private final List<ItemStack> cached;

    public AltsGUI(MurphyBan plugin, Player viewer, String label, List<UUID> accounts, Map<UUID, Boolean> banned) {
        super(plugin, viewer, "<gray>Alts — <white>" + escape(label) + "</white>", 3);
        this.cached = buildItems(accounts, banned);
    }

    private static List<ItemStack> buildItems(List<UUID> accounts, Map<UUID, Boolean> banned) {
        List<ItemStack> items = new ArrayList<>(accounts.size());
        for (UUID uuid : accounts) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            items.add(buildHead(op, banned.getOrDefault(uuid, Boolean.FALSE)));
        }
        return items;
    }

    private static ItemStack buildHead(OfflinePlayer op, boolean banned) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        meta.setOwnerProfile(op.getPlayerProfile());

        String displayName = op.getName() != null ? op.getName() : op.getUniqueId().toString();
        meta.setDisplayName(LEGACY.serialize(MM.deserialize("<white>" + escape(displayName) + "</white>")));

        List<String> lore = new ArrayList<>();
        lore.add(line("<gray>UUID: <white>" + op.getUniqueId() + "</white>"));
        lore.add(line(banned
                ? "<gray>Status: <red>Banned</red></gray>"
                : "<gray>Status: <green>Clean</green></gray>"));
        long lastSeen = op.getLastPlayed();
        String lastSeenText = lastSeen <= 0L ? "Unknown" : DATE_FMT.format(new Date(lastSeen));
        lore.add(line("<gray>Last seen: <white>" + lastSeenText + "</white>"));
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private static String line(String mm) {
        return LEGACY.serialize(MM.deserialize(mm));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "\\<");
    }

    @Override
    protected List<ItemStack> getItems() {
        return cached;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int slot, int page) {
        // Player heads are informational; no click action.
    }
}
