package com.donutsforlife11.donutgame.api.border;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
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

    private GameTimer centerTimer;
    private GameTimer dimensionsTimer;

    // private double particleSpacing = 1.0;
    // private double particleViewDistance = 32.0;
    // private Particle defaultParticle = Particle.TRIAL_OMEN;
    // private Particle movingParticle = Particle.RAID_OMEN;

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

    public GameBorder setCenter(Location target) {
        return setCenter(target, 0);
    }
    public GameBorder setCenter(Location target, int ticks) {
        if (target.getWorld() != center.getWorld()) {
            throw new IllegalArgumentException("Border center has to be in same world!");
        }
        centerTimer.cancel();
        if (ticks <= 0) {
            center = target.clone();
            return this;
        }
        Location start = center.clone();
        Location end = target.clone();
        int[] elapsed = {0};
        centerTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            double progress = ++elapsed[0] / (double) ticks;
            center.setX(lerp(start.getX(), end.getX(), progress));
            center.setY(lerp(start.getY(), end.getY(), progress));
            center.setZ(lerp(start.getZ(), end.getZ(), progress));
            if (elapsed[0] >= ticks) {
                center = end;
                centerTimer.cancel();
            }
        }).start();
        return this;
    }
    public GameBorder setDimensions(Vector target) {
        return setDimensions(target, 0);
    }
    public GameBorder setDimensions(Vector target, int ticks) {
        if (!(target.getX() >= 0 && target.getY() >= 0 && target.getZ() >= 0)) {
            throw new IllegalArgumentException("Border dimensions must be positive or zero!");
        };
        dimensionsTimer.cancel();
        if (ticks <= 0) {
            dimensions = target.clone();
            return this;
        }
        Vector start = dimensions.clone();
        Vector end = target.clone();
        int[] elapsed = {0};

        dimensionsTimer = timeManager.newTimer(ticks).onTick(ignored -> {
            double progress = ++elapsed[0] / (double) ticks;
            dimensions.setX(lerp(start.getX(), end.getX(), progress));
            dimensions.setY(lerp(start.getY(), end.getY(), progress));
            dimensions.setZ(lerp(start.getZ(), end.getZ(), progress));
            if (elapsed[0] >= ticks) {
                dimensions = end;
                dimensionsTimer.cancel();
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
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        List<Player> viewers = world.getNearbyPlayers(center, borderManager.particleViewDistance()).stream().toList();
        if (viewers.isEmpty()) {
            return;
        }
        List<Vector> points = BorderParticleSampler.sample(this, borderManager.particleSpacing(), 500);
        Particle selectedParticle = isMoving() ? borderManager.movingParticle() : borderManager.defaultParticle();
        for (Vector point : points) {
            Location location = center.clone().add(point);
            for (Player viewer : viewers) {
                viewer.spawnParticle(selectedParticle, location, 1, 0, 0, 0, 0);
            }
        }
    }

    public boolean isMoving() {
        return !centerTimer.isCancelled() || !dimensionsTimer.isCancelled();
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * Math.min(progress, 1.0);
    }
}
