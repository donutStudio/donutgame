package com.donutsforlife11.donutgame.game;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

public abstract class GameModule {
    protected GameContext context;
    protected GameMap map;
    protected TimeManager timeManager;
    protected PlayerManager playerManager;
    protected UIManager uiManager;

    public void onLoad(GameContext context) {
        return;
    }

    public void onStart() {
        return;
    }

    public void onStop() {
        return;
    }

    public void onUnload() {
        return;
    }

    public GameContext context() {
        return context;
    }
    public GameMap map() {
        return map;
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
}
