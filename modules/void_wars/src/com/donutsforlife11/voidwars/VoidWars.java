package com.donutsforlife11.voidwars;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.GameBossbar;
import com.donutsforlife11.donutgame.api.ui.GameSidebar;
import com.donutsforlife11.donutgame.api.ui.Values;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public class VoidWars extends GameModule {
    private boolean gameStarted = false;

    @Override
    public void onLoad(GameContext context) {
        playerManager.onPlayerRegistered(player -> {
            setupPlayer(player);
        });
        timeManager.createTimer(80).whileRunning(5, timer -> {
            context.getLogger().info("Ticks left: " + timer.getRemainingTicks());
        }).onEnd(timer -> {
            uiManager.title(Audience.audience(playerManager.getPlayers()), Component.text("phonk"));
            UiTest();
        }).start();
    }

    private void setupPlayer(Player player) {
        Location spawnPoint = map.getPoints("spawn").get(0); 
        player.teleportAsync(spawnPoint);
        playerManager.setPlayerSpawn(player, spawnPoint);
        playerManager.setPlayerSpawn(player, spawnPoint);

        if (gameStarted()) {
            playerManager.setSpectator(player);
            context.getLogger().info("sahur the goat");
        }

        uiManager.chat(player, Component.text("tung tung sahur"));
        uiManager.subtitle(player, Component.text("monkey corn"));
    }

    private void UiTest() {
        Audience players = Audience.audience(playerManager.getPlayers());
        GameTimer testTimer = timeManager.createTimer(400);
        Map<UUID, Integer> testPoints = new HashMap<>();

        GameSidebar testSidebar = uiManager.createSidebar()
            .setAudience(players)
            .addLine(Component.text("gooning"))
            .addLine(Values.component("Map", Component.text(map.getInstanceWorldName())))
            .addLine(Values.time("Time", testTimer::getRemainingSeconds))
            .addLine(Values.fraction("Alive Players", () -> playerManager.getNonSpectators().size(), () -> playerManager.getPlayers().size()))
            .addLine(Values.integer("Points", player -> testPoints.getOrDefault(player.getUniqueId(), 0)))
            .setUpdateInterval(10)
            .setVisibility(true);
        
            GameBossbar testBossbar = uiManager.createBossbar()
                .setAudience(players)
                .setTitle(Values.time("Time left", testTimer::getRemainingSeconds))
                .setValue(Values.integer("", testTimer.getRemainingTicks()))
                .setMax(400)
                .setStyle(BossBar.Overlay.NOTCHED_6)
                .setUpdateInterval(1)
                .setVisibility(true);
        
        testTimer.whileRunning(20, timer -> {
            for (Player player : playerManager.getPlayers()) {
                UUID uuid = player.getUniqueId();
                testPoints.put(uuid, testPoints.getOrDefault(uuid, 0) + new Random().nextInt(10) + 1);

                uiManager.actionbar(player, Component.text("You have " + testPoints.get(uuid) + " points!"));
            }
        });

        testTimer.onEnd(timer -> {
            testSidebar.addLine(Component.text("sigma"));
            testBossbar.remove();
        });

        testTimer.start();
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
        map.unloadWorld();
    }

    public boolean gameStarted() {
        return this.gameStarted;
    }
}