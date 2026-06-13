package com.donutsforlife11.donutgame.game;

import java.util.concurrent.CompletableFuture;

import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

public abstract class GameModule implements Listener {
    public static final String DEFAULT_MAP_PATH = MapManager.DEFAULT_MAP_PATH;

    protected GameContext context;
    protected GameMap map;
    protected GameWorld world;
    protected MapManager mapManager;
    protected TimeManager timeManager;
    protected PlayerManager playerManager;
    protected UIManager uiManager;
    protected TeamManager teamManager;
    protected BorderManager borderManager;
    protected YamlConfiguration config;
    protected String gameId;
    protected String gameName = gameId;

    public void beforeLoad(GameContext context) {
        context.mapManager().setMap(GameMap.fromPath(DEFAULT_MAP_PATH));
    }

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
    protected final CompletableFuture<Void> startLoadSequence() {
        int countdownTime = 200;
        try {
            beforeLoad(context);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }

        return mapManager.awaitIdle()
            .thenCompose(ignored -> mapManager.ensureWorld())
            .thenRun(() -> {
            onLoad(context);
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
            });
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
    public GameWorld world() {
        return world;
    }
    public MapManager mapManager() {
        return mapManager;
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
    public TeamManager teamManager() {
        return teamManager;
    }
    public BorderManager borderManager() {
        return borderManager;
    }
    public YamlConfiguration config() {
        return config;
    }
    public String gameId() {
        return gameId;
    }
}
