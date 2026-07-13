package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;

public class PlayerManager {
    private final Donutgame plugin;

    public PlayerManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public Donutgame plugin() {
        return plugin;
    }
    public List<Player> getPlayers() {
        return new ArrayList<>();
    };
}
