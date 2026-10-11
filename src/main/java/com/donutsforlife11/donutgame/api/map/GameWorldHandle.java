package com.donutsforlife11.donutgame.api.map;

import org.bukkit.World;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should use {@link GameWorld}'s scoped API instead.
 */
public final class GameWorldHandle {
    private GameWorldHandle() {
    }

    public static World bukkitWorld(GameWorld world) {
        return world == null ? null : world.bukkitWorld();
    }

    public static World requireBukkitWorld(GameWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        return world.requireBukkitWorld();
    }

    public static World requireBukkitWorld(GameWorld world, long generation) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        return world.requireBukkitWorld(generation);
    }

    public static void setBukkitWorld(GameWorld world, World bukkitWorld) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        world.setBukkitWorld(bukkitWorld);
    }

    public static long generation(GameWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        return world.generation();
    }
}
