package com.donutsforlife11.donutgame.game;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.event.Listener;

public abstract class GameModule implements Listener {
    protected GameContext context;
    protected GameMap map;
    protected TimeManager timeManager;
    protected PlayerManager playerManager;
    protected UIManager uiManager;
    protected String gameId;
    protected String gameName = gameId;

    public void onLoad(GameContext context) {
    }

    public void onStart() {
    }

    public void onStop() {
    }

    public void onUnload() {
    }

    protected void registerEvents(Listener listener) {
        context.registerEvents(listener);
    }
    protected void unregisterEvents(Listener listener) {
        context.unregisterEvents(listener);
    }
    protected void startLoadSequence() {
        int countdownTime = 200;
        timeManager.createTimer(50).onEnd(titleTimer -> {
            uiManager.title(Audience.audience(playerManager.getPlayers()), Component.text(gameName, NamedTextColor.LIGHT_PURPLE));
            timeManager.createTimer(50).onEnd(countdownTimer -> {
                timeManager.formattedCountdown(countdownTime, formattedNumber -> {
                    uiManager.title(Audience.audience(playerManager.getPlayers()), formattedNumber);
                }).onEnd(t -> {
                    uiManager.title(Audience.audience(playerManager.getPlayers()), Component.text("> START <").decorate(TextDecoration.BOLD));
                    onStart();
                }).start();
            }).start();
        }).start();
        onLoad(context);
    }

    public void setGameName(String name) {
        gameName = name;
    }
    public String gameName() {
        return gameName;
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
    public String gameId() {
        return gameId;
    }
}
