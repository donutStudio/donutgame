package com.donutsforlife11.donutgame.api.border;

import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.time.GameTimer;

public class GameBorder {
    private static final double EPSILON = 1.0e-6;

    private final BorderManager manager;
    private final BorderShape shape;
    private GameLocation center;
    private Vector dimensions;
    private GameTimer centerTimer;
    private GameTimer dimensionsTimer;

    GameBorder(BorderManager manager, BorderShape shape, GameLocation center, Vector dimensions) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.shape = Objects.requireNonNull(shape, "shape");
        this.center = requireLocation(center);
        this.dimensions = requireDimensions(dimensions);
    }

    public void remove() {
        centerTimer = cancel(centerTimer);
        dimensionsTimer = cancel(dimensionsTimer);
        manager.remove(this);
    }

    public GameBorder setCenter(GameLocation target) {
        return setCenter(target, 0);
    }

    public GameBorder setCenter(GameLocation target, int ticks) {
        GameLocation end = requireLocation(target);
        centerTimer = cancel(centerTimer);
        if (ticks <= 0) {
            center = end;
            return this;
        }

        GameLocation start = center;
        centerTimer = manager.timeManager().newTimer(ticks)
            .onTick(timer -> center = interpolate(start, end, timer.elapsedTicks() / (double) ticks))
            .onFinish(timer -> {
                center = end;
                centerTimer = null;
            })
            .start();
        return this;
    }

    public GameBorder setDimensions(Vector target) {
        return setDimensions(target, 0);
    }

    public GameBorder setDimensions(Vector target, int ticks) {
        Vector end = requireDimensions(target);
        dimensionsTimer = cancel(dimensionsTimer);
        if (ticks <= 0) {
            dimensions = end;
            return this;
        }

        Vector start = dimensions.clone();
        dimensionsTimer = manager.timeManager().newTimer(ticks)
            .onTick(timer -> dimensions = interpolate(start, end, timer.elapsedTicks() / (double) ticks))
            .onFinish(timer -> {
                dimensions = end.clone();
                dimensionsTimer = null;
            })
            .start();
        return this;
    }

    public boolean containsLocation(GameLocation location) {
        Objects.requireNonNull(location, "location");
        if (center.world() != null && location.world() != null && center.world() != location.world()) {
            return false;
        }
        return containsLocation(location.x() - center.x(), location.y() - center.y(), location.z() - center.z());
    }

    public boolean containsLocation(double x, double y, double z) {
        double radiusX = dimensions.getX() / 2.0;
        double radiusY = dimensions.getY() / 2.0;
        double radiusZ = dimensions.getZ() / 2.0;
        if (radiusX <= EPSILON || radiusY <= EPSILON || radiusZ <= EPSILON) {
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

    public boolean isMoving() {
        return active(centerTimer) || active(dimensionsTimer);
    }

    void drawParticles() {
        World world = resolveWorld();
        if (world == null) {
            return;
        }

        List<Player> viewers = manager.module().playerManager().getOnlinePlayers().stream()
            .map(gamePlayer -> gamePlayer.bukkitPlayer())
            .filter(player -> player != null && player.getWorld().equals(world))
            .toList();
        if (viewers.isEmpty()) {
            return;
        }

        Location centerLocation = center.toBukkit(world);
        Particle particle = isMoving() ? manager.movingParticle() : manager.defaultParticle();
        double viewDistanceSquared = squared(manager.particleViewDistance());
        int maximumParticles = shape == BorderShape.CUBOID ? 360 : 420;
        List<Vector> points = BorderParticleSampler.sample(this, manager.particleSpacing(), maximumParticles);

        for (Player viewer : viewers) {
            int spawned = 0;
            Location viewerLocation = viewer.getLocation();
            for (Vector point : points) {
                Location location = centerLocation.clone().add(point);
                if (viewerLocation.distanceSquared(location) > viewDistanceSquared) {
                    continue;
                }
                viewer.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0, null, true);
                spawned++;
            }
            if (spawned == 0 && shape != BorderShape.CUBOID) {
                drawNearbyCurvedSurface(viewer, particle, centerLocation, viewDistanceSquared);
            }
        }
    }

    private void drawNearbyCurvedSurface(Player viewer, Particle particle, Location centerLocation, double viewDistanceSquared) {
        Vector relative = viewer.getLocation().toVector().subtract(centerLocation.toVector());
        if (relative.lengthSquared() < EPSILON) {
            relative.setX(1.0);
        }

        Vector direction = relative.normalize();
        Vector surface = findSurfacePoint(direction);
        if (surface == null) {
            return;
        }

        Location base = centerLocation.clone().add(surface);
        if (viewer.getLocation().distanceSquared(base) > viewDistanceSquared) {
            return;
        }

        Vector tangentA = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (tangentA.lengthSquared() < EPSILON) {
            tangentA = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        tangentA.normalize();
        Vector tangentB = direction.clone().crossProduct(tangentA).normalize();
        double spacing = Math.max(1.25, manager.particleSpacing());

        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                Vector sampleDirection = surface.clone()
                    .add(tangentA.clone().multiply(a * spacing))
                    .add(tangentB.clone().multiply(b * spacing));
                if (sampleDirection.lengthSquared() < EPSILON) {
                    continue;
                }
                Vector sample = findSurfacePoint(sampleDirection.normalize());
                if (sample == null) {
                    continue;
                }
                Location location = centerLocation.clone().add(sample);
                if (viewer.getLocation().distanceSquared(location) <= viewDistanceSquared) {
                    viewer.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0, null, true);
                }
            }
        }
    }

    private Vector findSurfacePoint(Vector direction) {
        double maximumDistance = dimensions().multiply(0.5).length() * 1.1;
        if (maximumDistance <= EPSILON) {
            return null;
        }

        double insideDistance = 0.0;
        double outsideDistance = maximumDistance;
        for (int step = 0; step < 10; step++) {
            double distance = (insideDistance + outsideDistance) / 2.0;
            Vector point = direction.clone().multiply(distance);
            if (containsLocation(point.getX(), point.getY(), point.getZ())) {
                insideDistance = distance;
            } else {
                outsideDistance = distance;
            }
        }
        return direction.clone().multiply(insideDistance);
    }

    private World resolveWorld() {
        if (center.world() != null) {
            return center.world().bukkitWorld();
        }
        return manager.module().world().bukkitWorld();
    }

    private GameLocation requireLocation(GameLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        if (center != null && center.world() != null && location.world() != null && center.world() != location.world()) {
            throw new IllegalArgumentException("Border center cannot move between GameWorlds.");
        }
        return location;
    }

    private Vector requireDimensions(Vector dimensions) {
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions cannot be null");
        }
        if (dimensions.getX() < 0.0 || dimensions.getY() < 0.0 || dimensions.getZ() < 0.0) {
            throw new IllegalArgumentException("Border dimensions cannot be negative.");
        }
        return dimensions.clone();
    }

    private boolean active(GameTimer timer) {
        return timer != null && !timer.isFinished() && !timer.isCancelled();
    }

    private GameTimer cancel(GameTimer timer) {
        if (timer != null) {
            timer.cancel();
        }
        return null;
    }

    private GameLocation interpolate(GameLocation start, GameLocation end, double progress) {
        return new GameLocation(
            start.world() == null ? end.world() : start.world(),
            lerp(start.x(), end.x(), progress),
            lerp(start.y(), end.y(), progress),
            lerp(start.z(), end.z(), progress),
            (float) lerp(start.yaw(), end.yaw(), progress),
            (float) lerp(start.pitch(), end.pitch(), progress)
        );
    }

    private Vector interpolate(Vector start, Vector end, double progress) {
        return new Vector(
            lerp(start.getX(), end.getX(), progress),
            lerp(start.getY(), end.getY(), progress),
            lerp(start.getZ(), end.getZ(), progress)
        );
    }

    private static double lerp(double start, double end, double progress) {
        double clamped = Math.clamp(progress, 0.0, 1.0);
        return start + (end - start) * clamped;
    }

    private static double squared(double value) {
        return value * value;
    }
}
