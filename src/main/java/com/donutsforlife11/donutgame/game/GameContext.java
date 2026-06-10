package com.donutsforlife11.donutgame.game;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

public interface GameContext {
    public Plugin getPlugin();

    public Logger getLogger();

    public GameMap createMap(String mapPath);

    public CompletableFuture<GameMap> initializeMap(String mapPath);

    public MapManager mapManager();

    public TeamManager teamManager();

    public TimeManager timeManager();

    public PlayerManager playerManager();

    public UIManager uiManager();

    public void registerEvents(Listener listener);

    public void unregisterEvents(Listener listener);

    public YamlConfiguration getConfig();

    public int getGameIndex();

    public String getGameId();
}
