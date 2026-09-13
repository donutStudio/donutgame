package com.donutsforlife11.donutgame.api.player;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.game.GameModule;

final class PlayerTeleporter {
    private final GameModule module;

    PlayerTeleporter(GameModule module) {
        this.module = module;
    }

    CompletableFuture<Boolean> teleport(Player player, Location destination) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                destination.getChunk().load();
                player.closeInventory();
                player.teleportAsync(destination).whenComplete((teleported, throwable) ->
                    Bukkit.getScheduler().runTask(module.plugin(), () -> {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                            return;
                        }
                        result.complete(Boolean.TRUE.equals(teleported));
                    })
                );
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(module.plugin(), task);
        }
        return result;
    }

    World defaultWorld() {
        GameWorld gameWorld = module.defaultWorld();
        World world = gameWorld == null ? null : gameWorld.bukkitWorld();
        if (world == null) {
            throw new IllegalStateException("Game " + module.index() + " does not have a loaded default world.");
        }
        return world;
    }

    Location fallbackLocation(World currentWorld) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(currentWorld)) {
                return world.getSpawnLocation();
            }
        }
        throw new IllegalStateException("Cannot move player out of game " + module.index() + " because no destination world is loaded.");
    }
}
