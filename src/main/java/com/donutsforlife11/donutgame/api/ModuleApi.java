package com.donutsforlife11.donutgame.api;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameMap;

public class ModuleApi implements GameContext {
    private final Donutgame plugin;

    public ModuleApi(Donutgame plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public CompletableFuture<GameMap> initializeMap(String mapPath) {
        GameMap map = new GameMap(plugin, new File(plugin.getMapsFolder(), mapPath));
        return map.loadWorld();
    }
}
