package com.donutsforlife11.voidwars;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameMap;
import com.donutsforlife11.donutgame.game.GameModule;

public class VoidWars extends GameModule {
    private GameContext context;
    private GameMap map;
    private TimeManager timeManager;

    @Override
    public void onLoad(GameContext context) {
        this.context = context;
        context.initializeMap("void_wars/sky_meadows.yml").thenAccept(map -> {
            this.map = map;

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleportAsync(this.map.getPoints("spawn").get(0));
            }
        });
        context.getLogger().info("tung tung sahur");
        timeManager = context.timeManager();
        GameTimer testTimer = timeManager.createTimer(200);
        testTimer.whileRunning(5, timer -> {
            context.getLogger().info("Ticks left: " + timer.getRemainingTicks());
        })
        .onEnd(timer -> {
            timeManager.createTimer().whileRunning(t -> {context.getLogger().info("saaaa-huur");}).start();
        }).start();
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
        map.unloadWorld();
    }
}