package com.donutsforlife11.donutgame.internal.game;

import java.util.concurrent.CompletableFuture;

import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

public abstract class GameModule {
    private MapManager mapManager;
    private PlayerManager playerManager;
    private int index;

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
        MapManager mapManager
    ) {
        this.index = index;
        this.playerManager = playerManager;
        this.mapManager = mapManager;
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