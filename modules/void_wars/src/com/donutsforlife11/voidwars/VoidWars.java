package com.donutsforlife11.voidwars;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;

public class VoidWars extends GameModule {
    public boolean gameStarted = false;

    @Override
    public void beforeLoad(GameContext context) {
        mapManager.setMap(GameMap.fromPath("void_wars/sky_meadows.dmap"));
    }

    @Override
    public void onLoad(GameContext context) {
        playerManager.onPlayerEnteredWorld(this::setupPlayer);

        registerEvents(new VoidWarsEvents(this));
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

    public boolean gameStarted() {
        return gameStarted;
    }

    private void setupPlayer(Player player) {
        Location spawnPoint = world.getPoints("spawn").getFirst();
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
