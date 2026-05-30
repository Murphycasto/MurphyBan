package com.murphy.ban.gui;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.model.Punishment;
import com.murphy.ban.model.PunishmentType;
import com.murphy.ban.util.PunishmentFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HistoryGUI extends PaginatedGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy HH:mm");
    private static final Map<PunishmentType, Material> ICONS = new EnumMap<>(PunishmentType.class);

    static {
        ICONS.put(PunishmentType.BAN, Material.RED_WOOL);
        ICONS.put(PunishmentType.IP_BAN, Material.MAGENTA_WOOL);
        ICONS.put(PunishmentType.MUTE, Material.ORANGE_WOOL);
        ICONS.put(PunishmentType.KICK, Material.YELLOW_WOOL);
        ICONS.put(PunishmentType.WARN, Material.LIME_WOOL);
    }

    private final List<ItemStack> cached;

    public HistoryGUI(MurphyBan plugin, Player viewer, String title, List<Punishment> punishments) {
        super(plugin, viewer, title, 5);
        this.cached = buildItems(punishments);
    }

    private static List<ItemStack> buildItems(List<Punishment> punishments) {
        List<ItemStack> items = new ArrayList<>(punishments.size());
        for (Punishment p : punishments) {
            items.add(toItem(p));
        }
        return items;
    }

    private static ItemStack toItem(Punishment p) {
        Material material = ICONS.getOrDefault(p.type(), Material.PAPER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        boolean isKick = p.type() == PunishmentType.KICK;
        String name = "<white>" + p.type().getDisplayName() + " — " + PunishmentFormatter.resolveStatus(p);
        meta.setDisplayName(LEGACY.serialize(MM.deserialize(name)));

        List<String> lore = new ArrayList<>();
        lore.add(line("<gray>Player: <white>" + escape(p.playerName()) + "</white>"));
        lore.add(line("<gray>Reason: <white>" + escape(p.reason()) + "</white>"));
        lore.add(line("<gray>Issued by: <white>" + escape(p.issuedBy()) + "</white>"));
        lore.add(line("<gray>Issued at: <white>" + DATE_FMT.format(new Date(p.issuedAt())) + "</white>"));
        if (!isKick) {
            lore.add(line("<gray>Duration: <white>" + p.getFormattedDuration() + "</white>"));
            String expiryText = p.isRevoked() ? "Revoked" : p.getFormattedExpiry();
            lore.add(line("<gray>Expires: <white>" + expiryText + "</white>"));
        }
        if (p.isRevoked()) {
            lore.add(line("<gray>Revoked by: <white>" + escape(p.revokedBy()) + "</white>"));
            String revokedAtText = p.revokedAt() > 0L
                    ? DATE_FMT.format(new Date(p.revokedAt()))
                    : "Unknown";
            lore.add(line("<gray>Revoked at: <white>" + revokedAtText + "</white>"));
        }
        lore.add(line("<gray>ID: <white>#" + p.id() + "</white>"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static String line(String mm) {
        return LEGACY.serialize(MM.deserialize(mm));
    }

    private static String escape(String s) {
        return PunishmentFormatter.sanitize(s).replace("<", "\\<");
    }

    @Override
    protected List<ItemStack> getItems() {
        return cached;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int slot, int page) {
        // Content items are informational; no click action.
    }
}
