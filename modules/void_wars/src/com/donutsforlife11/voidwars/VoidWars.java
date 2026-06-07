package com.donutsforlife11.voidwars;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;

public class VoidWars extends GameModule {
    public boolean gameStarted = false;

    @Override
    public void onLoad(GameContext context) {
        playerManager.onPlayerRegistered(player -> {
            setupPlayer(player);
        });
    }

    @Override
    public void onStart() {
        gameStarted = true;
        for (Player player : playerManager.getNonSpectators()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
    }

    private void setupPlayer(Player player) {
        Location spawnPoint = map.getPoints("spawn").get(0); 
        player.teleportAsync(spawnPoint);
        playerManager.setPlayerSpawn(player, spawnPoint);
        if (gameStarted) {
            playerManager.setSpectator(player);
        } else {
            playerManager.setNonSpectator(player);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }
}
