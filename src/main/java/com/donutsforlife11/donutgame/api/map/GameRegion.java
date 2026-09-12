package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;

public class GameRegion {
    private final GameLocation min;
    private final GameLocation max;

    public GameRegion(GameLocation first, GameLocation second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        min = new GameLocation(
            Math.min(first.x(), second.x()),
            Math.min(first.y(), second.y()),
            Math.min(first.z(), second.z())
        );
        max = new GameLocation(
            Math.max(first.x(), second.x()),
            Math.max(first.y(), second.y()),
            Math.max(first.z(), second.z())
        );
    }

    public GameLocation min() {
        return min;
    }

    public GameLocation max() {
        return max;
    }

    public GameLocation center() {
        return new GameLocation(
            (min.x() + max.x()) / 2.0,
            (min.y() + max.y()) / 2.0,
            (min.z() + max.z()) / 2.0
        );
    }

    public boolean contains(GameLocation location) {
        Objects.requireNonNull(location, "location");
        return location.x() >= min.x() && location.x() <= max.x()
            && location.y() >= min.y() && location.y() <= max.y()
            && location.z() >= min.z() && location.z() <= max.z();
    }

    public double sizeX() {
        return max.x() - min.x();
    }

    public double sizeY() {
        return max.y() - min.y();
    }

    public double sizeZ() {
        return max.z() - min.z();
    }

    public double volume() {
        return sizeX() * sizeY() * sizeZ();
    }

}
