package com.donutsforlife11.donutgame.api;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameMap;

public class ModuleApi implements GameContext {
    private final Donutgame plugin;
    private final int gameIndex;
    private final String gameId;
    private final YamlConfiguration config;
    private final TeamManager teamManager;
    private final TimeManager timeManager;
    private final PlayerManager playerManager;
    private final UIManager uiManager;
    private final Set<Listener> registeredListeners = new LinkedHashSet<>();

    public ModuleApi(
        Donutgame plugin,
        int gameIndex,
        String gameId,
        YamlConfiguration config,
        TeamManager teamManager,
        TimeManager timeManager,
        PlayerManager playerManager,
        UIManager uiManager
    ) {
        this.plugin = plugin;
        this.gameIndex = gameIndex;
        this.gameId = gameId;
        this.config = config;
        this.teamManager = teamManager;
        this.timeManager = timeManager;
        this.playerManager = playerManager;
        this.uiManager = uiManager;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public GameMap createMap(String mapPath) {
        return new GameMap(plugin, new File(plugin.getMapsFolder(), mapPath));
    }

    public CompletableFuture<GameMap> initializeMap(String mapPath) {
        return createMap(mapPath).loadWorld();
    }

    public TeamManager teamManager() {
        return teamManager;
    }

    public TimeManager timeManager() {
        return timeManager;
    }

    public PlayerManager playerManager() {
        return playerManager;
    }

    public UIManager uiManager() {
        return uiManager;
    }

    public void registerEvents(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (registeredListeners.add(listener)) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    public void unregisterEvents(Listener listener) {
        if (listener != null && registeredListeners.remove(listener)) {
            HandlerList.unregisterAll(listener);
        }
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public int getGameIndex() {
        return gameIndex;
    }

    public String getGameId() {
        return gameId;
    }

    public PlayerManager getInternalPlayerManager() {
        return playerManager;
    }

    public void shutdown() {
        for (Listener listener : Set.copyOf(registeredListeners)) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        uiManager.shutdown();
        playerManager.shutdown();
        timeManager.shutdown();
        teamManager.clear();
    }
}
