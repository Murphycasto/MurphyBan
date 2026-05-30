package com.murphy.ban.commands;

import com.murphy.ban.MurphyBan;
import com.murphy.ban.gui.PunishmentListGUI.CategoryFilter;

public class MuteListCommand extends PunishmentListCommand {

    public MuteListCommand(MurphyBan plugin) {
        super(plugin, CategoryFilter.MUTE);
    }
}
