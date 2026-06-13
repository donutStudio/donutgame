package com.donutsforlife11.donutgame.api.border;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.time.GameTimer;

public class GameBorder {
    private final BorderManager borderManager;
    private final BorderShape shape;

    private Location center;
    private Vector dimensions;
    private GameTimer centerTimer;
    private GameTimer dimensionsTimer;

    public GameBorder(BorderManager borderManager, BorderShape shape, Location center, Vector dimensions) {
        this.borderManager = borderManager;
        this.shape = shape;
        this.center = center.clone();
        this.dimensions = dimensions.clone();
    }

    public BorderShape getShape() {
        return shape;
    }

    public Location getCenter() {
        return center.clone();
    }

    public Vector getDimensions() {
        return dimensions.clone();
    }

    public GameBorder setDimensions(Vector dimensions) {
        return setDimensions(dimensions, 0);
    }

    public GameBorder setDimensions(Vector dimensions, int time) {
        Vector target = borderManager.requireDimensions(dimensions);

        if (time <= 0) {
            cancelDimensionsTimer();
            this.dimensions = target;
            return this;
        }

        Vector start = this.dimensions.clone();
        cancelDimensionsTimer();
        dimensionsTimer = borderManager.createTransitionTimer(time, fraction -> {
            this.dimensions = start.clone().multiply(1.0 - fraction).add(target.clone().multiply(fraction));
        }, () -> {
            this.dimensions = target;
            dimensionsTimer = null;
        });
        return this;
    }

    public GameBorder setCenter(Location center) {
        return setCenter(center, 0);
    }

    public GameBorder setCenter(Location center, int time) {
        Location target = borderManager.requireCenter(center);

        if (time <= 0) {
            cancelCenterTimer();
            this.center = target;
            return this;
        }

        Location start = this.center.clone();
        if (!start.getWorld().equals(target.getWorld())) {
            throw new IllegalArgumentException("Timed border movement must stay in the same world.");
        }

        cancelCenterTimer();
        centerTimer = borderManager.createTransitionTimer(time, fraction -> {
            Vector blended = start.toVector().multiply(1.0 - fraction).add(target.toVector().multiply(fraction));
            this.center = blended.toLocation(start.getWorld(), start.getYaw(), start.getPitch());
        }, () -> {
            this.center = target;
            centerTimer = null;
        });
        return this;
    }

    public boolean isMoving() {
        return centerTimer != null || dimensionsTimer != null;
    }

    public boolean containsLocation(Location location) {
        return borderManager.borderContainsLocation(this, location);
    }

    public void deleteBorder() {
        cancelCenterTimer();
        cancelDimensionsTimer();
        borderManager.deleteBorder(this);
    }

    private void cancelCenterTimer() {
        if (centerTimer != null) {
            centerTimer.cancel();
            centerTimer = null;
        }
    }

    private void cancelDimensionsTimer() {
        if (dimensionsTimer != null) {
            dimensionsTimer.cancel();
            dimensionsTimer = null;
        }
    }
}
