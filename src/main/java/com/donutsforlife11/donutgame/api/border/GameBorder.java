package com.donutsforlife11.donutgame.api.border;

import java.util.List;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;

public class GameBorder {
    private final BorderManager borderManager;
    private final TimeManager timeManager;

    private final BorderShape shape;
    private GameLocation center;
    private Vector dimensions;

    private GameTimer centerTimer;
    private GameTimer dimensionsTimer;

    public GameBorder(BorderManager borderManager, TimeManager timeManager, BorderShape shape, GameLocation center, Vector dimensions) {
        this.borderManager = borderManager;
        this.timeManager = timeManager;
        this.shape = shape;
        this.center = center;
        this.dimensions = dimensions;
    }

    void remove() {
        borderManager.borders().remove(this);
    }

    public GameBorder setCenter(GameLocation target) {
        return setCenter(target, 0);
    }

    public GameBorder setCenter(GameLocation target, int ticks) {
        if (centerTimer != null) {
            centerTimer.cancel();
        }
        if (ticks <= 0) {
            center = target;
            return this;
        }
        GameLocation start = center;
        centerTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            double progress = ignored.getElapsedTicks() / (double) ticks;
            center = new GameLocation(
                lerp(start.x(), target.x(), progress),
                lerp(start.y(), target.y(), progress),
                lerp(start.z(), target.z(), progress),
                lerp(start.pitch(), target.pitch(), progress),
                lerp(start.yaw(), target.yaw(), progress)
            );
        }).onFinish(ignored -> center = target).start();
        return this;
    }

    public GameBorder setDimensions(Vector target) {
        return setDimensions(target, 0);
    }

    public GameBorder setDimensions(Vector target, int ticks) {
        if (target.getX() < 0 || target.getY() < 0 || target.getZ() < 0) {
            throw new IllegalArgumentException("Border dimensions must be positive or zero!");
        }
        if (dimensionsTimer != null) {
            dimensionsTimer.cancel();
        }
        if (ticks <= 0) {
            dimensions = target.clone();
            return this;
        }
        Vector start = dimensions.clone();
        Vector end = target.clone();
        dimensionsTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            double progress = ignored.getElapsedTicks() / (double) ticks;
            dimensions = new Vector(
                lerp(start.getX(), end.getX(), progress),
                lerp(start.getY(), end.getY(), progress),
                lerp(start.getZ(), end.getZ(), progress)
            );
        }).onFinish(ignored -> dimensions = end).start();
        return this;
    }

    public boolean containsLocation(GameLocation location) {
        return containsLocation(location.x() - center.x(), location.y() - center.y(), location.z() - center.z());
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

    public BorderShape shape() {
        return shape;
    }

    public GameLocation center() {
        return center;
    }

    public Vector dimensions() {
        return dimensions.clone();
    }

    void drawParticles() {
        World world = borderManager.borders().stream().findFirst().map(ignored -> borderManager.module().world().bukkitWorld()).orElse(null);
        if (world == null) {
            return;
        }
        List<Player> viewers = world.getNearbyPlayers(center.toBukkit(world), borderManager.particleViewDistance()).stream().toList();
        if (viewers.isEmpty()) {
            return;
        }
        List<Vector> points = BorderParticleSampler.sample(this, borderManager.particleSpacing(), 500);
        Particle selectedParticle = isMoving() ? borderManager.movingParticle() : borderManager.defaultParticle();
        for (Vector point : points) {
            var location = center.toBukkit(world).add(point);
            for (Player viewer : viewers) {
                viewer.spawnParticle(selectedParticle, location, 1, 0, 0, 0, 0);
            }
        }
    }

    public boolean isMoving() {
        return (centerTimer != null && !centerTimer.isFinished() && !centerTimer.isCancelled())
            || (dimensionsTimer != null && !dimensionsTimer.isFinished() && !dimensionsTimer.isCancelled());
    }

    private double squared(double input) {
        return input * input;
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * Math.min(progress, 1.0);
    }
}
