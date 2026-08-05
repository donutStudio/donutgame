package com.donutsforlife11.donutgame.api.border;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;

public class GameBorder {
    private final BorderManager borderManager;
    private final TimeManager timeManager;

    private final BorderShape shape;
    private Location center;
    private Vector dimensions;

    private GameTimer centerTimer = null;
    private GameTimer dimensionsTimer = null;

    private double particleSpacing = 1.0;
    private double particleViewDistance = 32.0;

    public GameBorder(BorderManager borderManager, TimeManager timeManager, BorderShape shape, Location center, Vector dimensions) {
        this.borderManager = borderManager;
        this.timeManager = timeManager;
        this.shape = shape;
        this.center = center;
        this.dimensions = dimensions;
    }
    void remove() {
        borderManager.borders().remove(this);
    }

    public GameBorder setCenter(Location targetCenter) {
        return setCenter(targetCenter, 0);
    }
    public GameBorder setCenter(Location targetCenter, int ticks) {
        if (targetCenter.getWorld() != center.getWorld()) {
            throw new IllegalArgumentException("Border center has to be in same world!");
        }
        Location target = targetCenter;
        
        double remainingX = target.getX() - dimensions.getX();
        double remainingY = target.getY() - dimensions.getY();
        double remainingZ = target.getZ() - dimensions.getZ();

        double xStep = remainingX / ticks;
        double yStep = remainingY / ticks;
        double zStep = remainingZ / ticks;

        if (centerTimer != null) {
            centerTimer.cancel();
        }
        centerTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            if (remainingX != 0) {
                center.setX(remainingX > 0 ? center.getX() + xStep : center.getX() - xStep);
            }
            if (remainingY != 0) {
                center.setY(remainingY > 0 ? center.getY() + yStep : center.getY() - yStep);
            }
            if (remainingZ != 0) {
                center.setZ(remainingZ > 0 ? center.getZ() + zStep : center.getZ() - zStep);
            }
        }).start();

        return this;
    }
    public GameBorder setDimensions(Vector targetDimensions) {
        return setDimensions(targetDimensions, 0);
    }
    public GameBorder setDimensions(Vector targetDimensions, int ticks) {
        if (!(targetDimensions.getX() >= 0 && targetDimensions.getY() >= 0 && targetDimensions.getZ() >= 0)) {
            throw new IllegalArgumentException("Border dimensions must be positive or zero!");
        };
        Vector target = targetDimensions;

        double remainingX = target.getX() - dimensions.getX();
        double remainingY = target.getY() - dimensions.getY();
        double remainingZ = target.getZ() - dimensions.getZ();

        double xStep = remainingX / ticks;
        double yStep = remainingY / ticks;
        double zStep = remainingZ / ticks;

        if (dimensionsTimer != null) {
            dimensionsTimer.cancel();
        }
        dimensionsTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            if (remainingX != 0) {
                dimensions.setX(remainingX > 0 ? dimensions.getX() + xStep : dimensions.getX() - xStep);
            }
            if (remainingY != 0) {
                dimensions.setX(remainingY > 0 ? dimensions.getY() + yStep : dimensions.getZ() - yStep);
            }
            if (remainingZ != 0) {
                dimensions.setZ(remainingZ > 0 ? dimensions.getZ() + zStep : dimensions.getZ() - zStep);
            }
        }).start();

        return this;
    }

    public boolean containsLocation(Location location) {
        if (location.getWorld() != center.getWorld()) {
            return false;
        }
        return containsLocation(location.getX() - center.getX(), location.getY() - center.getY(), location.getZ() - center.getZ());
    }
    public boolean containsLocation(double x, double y, double z) {
        double radiusX = dimensions.getX() / 2.0;
        double radiusY = dimensions.getY() / 2.0;
        double radiusZ = dimensions.getZ() / 2.0;
        if (radiusX <= 0.0 || radiusY <= 0.0 || radiusZ <= 0.0) {
            return false;
        }
        return switch (shape) {
            case CUBOID -> Math.abs(x) <= radiusX && Math.abs(y) <= radiusY && Math.abs(z) <= radiusZ;
            case CYLINDROID -> squared(x / radiusX) + squared(z / radiusZ) <= 1.0 && Math.abs(y) <= radiusY;
            case ELLIPSOID -> squared(x / radiusX) + squared(y / radiusY) + squared(z / radiusZ) <= 1.0;
        };
    }
    private double squared(double input) {
        return input * input;
    }


    public BorderShape shape() {
        return shape;
    }
    public Location center() {
        return center;
    }
    public Vector dimensions() {
        return dimensions;
    }

    void drawParticles() {

    }
}
