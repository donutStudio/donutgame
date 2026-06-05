package com.donutsforlife11.donutgame.api;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameMap;

public class ModuleApi implements GameContext {
    private final Donutgame plugin;
    private final TeamManager teamManager;

    public ModuleApi(Donutgame plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
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

    public TeamManager teamManager() {
        return teamManager;
    }
}
