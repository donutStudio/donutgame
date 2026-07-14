package com.donutsforlife11.donutgame.internal.game;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;

import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.ui.UiManager;

public abstract class GameModule {
    private MapManager mapManager;
    private PlayerManager playerManager;
    private UiManager uiManager;
    private int index;
    private String id;
    private String name;

    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

    public void beforeLoad() {
    }

    public void onLoad() {
    }

    public void onUnload() {
    }

    protected final CompletableFuture<GameModule> startLoadSequence() {
        try {
            beforeLoad();
            onLoad();
            return CompletableFuture.completedFuture(this);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
    protected final CompletableFuture<Void> shutdown() {
        return mapManager.unloadCurrentWorld();
    }
    protected final void initialize(
        GameModuleDescriptor descriptor, 
        int index, 
        PlayerManager playerManager, 
        MapManager mapManager,
        UiManager uiManager
    ) {
        this.index = index;
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.uiManager = uiManager;
    }
    public final Scoreboard scoreboard() {
        return scoreboard;
    }

    public String id() {
        return id;
    }
    public String name() {
        return name;
    }
    public int index() {
        return index;
    }
    public PlayerManager playerManager() {
        return playerManager;
    }
    public MapManager mapManager() {
        return mapManager;
    }
}