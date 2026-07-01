package com.donutsforlife11.donutgame.internal.module;

import java.util.concurrent.CompletableFuture;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

public class GameContext {
    private final Plugin plugin;

    public GameContext(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
    public void registerEvents(Listener listener) {
        
    }

    public PlayerManager playerManager() {
        return new PlayerManager(plugin);
    }
    public MapManager mapManager() {
        return new MapManager();
    }
}
