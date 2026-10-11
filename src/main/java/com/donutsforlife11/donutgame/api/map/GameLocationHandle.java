package com.donutsforlife11.donutgame.api.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should pass {@link GameLocation} to scoped API methods instead.
 */
public final class GameLocationHandle {
    private GameLocationHandle() {
    }

    public static Location bukkitLocation(GameLocation location) {
        return location == null ? null : location.bukkitLocation();
    }

    public static GameLocation create(GameWorld world, Location location) {
        return location == null ? null : new GameLocation(world, location);
    }

    public static Location toBukkit(GameLocation location, World world) {
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        return location.toBukkit(world);
    }
}
