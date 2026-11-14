package com.donutsforlife11.donutgame.api.map;

import org.bukkit.Location;

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

    public static boolean isCurrent(GameLocation location) {
        return location.world() == null || location.generation() == location.world().generation();
    }

    public static void requireCurrent(GameLocation location) {
        if (!isCurrent(location)) {
            location.world().requireCurrentGeneration(location.generation(), "GameLocation");
        }
    }

    public static Location toBukkit(GameLocation location, GameWorld world) {
        return world.toBukkit(location);
    }


}
