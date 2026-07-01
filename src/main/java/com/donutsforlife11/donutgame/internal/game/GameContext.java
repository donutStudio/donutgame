package com.donutsforlife11.donutgame.internal.game;

import java.util.concurrent.CompletableFuture;

public class GameContext {
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}
