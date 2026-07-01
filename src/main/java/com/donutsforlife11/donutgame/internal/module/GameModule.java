package com.donutsforlife11.donutgame.internal.module;

import java.util.concurrent.CompletableFuture;

import org.bukkit.event.Listener;

import com.donutsforlife11.donutgame.api.map.GameWorld;

public abstract class GameModule implements Listener {
    protected GameWorld world;

    public void onLoad(GameContext context) {
    }

    public void onUnload() {

    }

    public CompletableFuture<Void> startLoadSequence() {
        return CompletableFuture.completedFuture(null);
    }
}
