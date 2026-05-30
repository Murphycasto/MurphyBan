package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.gui.PunishmentListGUI.CategoryFilter;

public class BanListCommand extends PunishmentListCommand {

    public BanListCommand(MurphyBan plugin) {
        super(plugin, CategoryFilter.BAN);
    }
}
