package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;

public class GameRegion {
    private final GameWorld world;
    private final GameLocation min;
    private final GameLocation max;

    public GameRegion(GameLocation first, GameLocation second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        world = regionWorld(first, second);
        min = new GameLocation(
            world,
            Math.min(first.x(), second.x()),
            Math.min(first.y(), second.y()),
            Math.min(first.z(), second.z())
        );
        max = new GameLocation(
            world,
            Math.max(first.x(), second.x()),
            Math.max(first.y(), second.y()),
            Math.max(first.z(), second.z())
        );
    }

    public GameWorld world() {
        return world;
    }

    public GameLocation min() {
        return min;
    }

    public GameLocation max() {
        return max;
    }

    public GameLocation center() {
        return new GameLocation(
            world,
            (min.x() + max.x()) / 2.0,
            (min.y() + max.y()) / 2.0,
            (min.z() + max.z()) / 2.0
        );
    }

    public boolean contains(GameLocation location) {
        Objects.requireNonNull(location, "location");
        if (world != null && location.world() != null && location.world() != world) {
            return false;
        }
        return location.x() >= min.x() && location.x() <= max.x()
            && location.y() >= min.y() && location.y() <= max.y()
            && location.z() >= min.z() && location.z() <= max.z();
    }

    public double sizeX() {
        return max.blockX() - min.blockX() + 1;
    }

    public double sizeY() {
        return max.blockY() - min.blockY() + 1;
    }

    public double sizeZ() {
        return max.blockZ() - min.blockZ() + 1;
    }

    public double volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    private static GameWorld regionWorld(GameLocation first, GameLocation second) {
        GameWorld firstWorld = first.world();
        GameWorld secondWorld = second.world();
        if (firstWorld != null && secondWorld != null && firstWorld != secondWorld) {
            throw new IllegalArgumentException("Region endpoints must belong to the same GameWorld.");
        }
        return firstWorld == null ? secondWorld : firstWorld;
    }

}
