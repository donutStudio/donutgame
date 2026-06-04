package com.donutsforlife11.donutgame.game;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

public interface GameContext {
    public Plugin getPlugin();

    public Logger getLogger();

    public CompletableFuture<GameMap> initializeMap(String mapPath);
}
