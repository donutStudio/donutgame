package com.donutsforlife11.donutgame.internal.game;

import java.util.concurrent.CompletableFuture;

public abstract class GameModule {
    protected GameContext context;
    protected String id;
    protected String name;

    public void beforeLoad(GameContext context) {
        
    }

    public void onLoad(GameContext context) {

    }

    public void onUnload() {

    }

    public CompletableFuture<Void> startLoadSequence() {
        return CompletableFuture.completedFuture(null);
    }
    public GameContext context() {
        return context;
    }
}
