package com.donutsforlife11.donutgame.api.ui.title;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;

public class TitlePacketTracker {
    public TitlePacketTracker(Donutgame plugin) {
    }

    public boolean available() {
        return false;
    }

    public boolean hasActiveTitle(Player player) {
        return false;
    }
}
