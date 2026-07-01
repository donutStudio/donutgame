package com.donutsforlife11.donutgame.api.map;

import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

public class MapManager {
    private volatile GameWorld currentWorld;

    private final PlayerManager playerManager;

    public MapManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public CompletableFuture<Boolean> teleportPlayerToSpawn(Player player) {
        if (currentWorld == null) {
            return CompletableFuture.completedFuture(false);
        }

        World targetWorld = currentWorld.bukkitWorld();
        boolean alreadyInWorld = targetWorld.equals(player.getWorld());
        CompletableFuture<Boolean> future = player.teleportAsync(targetWorld.getSpawnLocation());
        future.thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && alreadyInWorld) {
                playerManager.notifyPlayerEnteredWorld(player);
            }
        });
        return future;
    }
}
