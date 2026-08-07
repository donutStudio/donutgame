package com.donutsforlife11.donutgame.api.map;

import org.bukkit.util.BoundingBox;

public class GameRegion {
    private final GameLocation min;
    private final GameLocation max;

    public GameRegion(GameLocation first, GameLocation second) {
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

    public static GameRegion fromBoundingBox(BoundingBox box) {
        return new GameRegion(
            new GameLocation(box.getMinX(), box.getMinY(), box.getMinZ()),
            new GameLocation(box.getMaxX(), box.getMaxY(), box.getMaxZ())
        );
    }

    public boolean contains(GameLocation location) {
        return location.x() >= min.x() && location.x() <= max.x()
            && location.y() >= min.y() && location.y() <= max.y()
            && location.z() >= min.z() && location.z() <= max.z();
    }

    public GameLocation center() {
        return new GameLocation(
            (min.x() + max.x()) / 2.0,
            (min.y() + max.y()) / 2.0,
            (min.z() + max.z()) / 2.0
        );
    }

    BoundingBox toBoundingBox() {
        return new BoundingBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public GameLocation min() {
        return min;
    }

    public GameLocation max() {
        return max;
    }
}
