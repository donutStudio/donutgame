package com.donutsforlife11.donutgame.api.ui;

public class GameGlow {
    private final Runnable clearAction;
    private boolean cleared;

    public GameGlow(Runnable clearAction) {
        this.clearAction = clearAction == null ? () -> {} : clearAction;
    }

    public void clear() {
        if (cleared) return;
        cleared = true;
        clearAction.run();
    }
}
