package com.donutsforlife11.voidwars;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameMap;
import com.donutsforlife11.donutgame.game.GameModule;

public class VoidWars extends GameModule {
    private GameContext context;
    private GameMap map;
    private TimeManager timeManager;
    private PlayerManager playerManager;

    private boolean gameStarted = false;
    private PlayerService playerService;

    @Override
    public void onLoad(GameContext context) {
        this.context = context;
        this.playerManager = context.playerManager();
        this.timeManager = context.timeManager();
        this.playerService = new PlayerService(this, this.playerManager);
        context.initializeMap("void_wars/sky_meadows.yml").thenAccept(map -> {
            this.map = map;
            for (Player player : playerManager.getPlayers()) {
                player.teleportAsync(map.getPoints("spawn").get(0));
            }
        });

        playerService.playerEvents();
        timeManager.createTimer(200).whileRunning(5, timer -> {
            context.getLogger().info("Ticks left: " + timer.getRemainingTicks());
        }).start();
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
        map.unloadWorld();
    }

    public boolean gameStarted() {
        return this.gameStarted;
    }

    public GameMap getMap() {
        return this.map;
    }

    public GameContext getContext() {
        return this.context;
    }
}