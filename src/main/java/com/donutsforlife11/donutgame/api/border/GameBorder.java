package com.donutsforlife11.donutgame.api.border;

import java.util.List;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;
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

    public void remove() {
        if (centerTimer != null) {
            centerTimer.cancel();
            centerTimer = null;
        }
        if (dimensionsTimer != null) {
            dimensionsTimer.cancel();
            dimensionsTimer = null;
        }
        borderManager.borders().remove(this);
    }

    public GameBorder setCenter(GameLocation target) {
        return setCenter(target, 0);
    }

    public GameBorder setCenter(double x, double y, double z) {
        return setCenter(new GameLocation(x, y, z));
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

    public GameBorder setCenter(double x, double y, double z, int ticks) {
        return setCenter(new GameLocation(x, y, z), ticks);
    }

    public GameBorder setDimensions(Vector target) {
        return setDimensions(target, 0);
    }

    public GameBorder setDimensions(double x, double y, double z) {
        return setDimensions(new Vector(x, y, z));
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

    public GameBorder setDimensions(double x, double y, double z, int ticks) {
        return setDimensions(new Vector(x, y, z), ticks);
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
        World world = borderManager.module().world().bukkitWorld();
        if (world == null) {
            return;
        }

        List<Player> viewers = borderManager.module().playerManager().getPlayers().stream()
            .map(gamePlayer -> gamePlayer.player())
            .filter(player -> player != null && player.getWorld().equals(world))
            .toList();

        if (viewers.isEmpty()) {
            return;
        }

        Particle particle = isMoving() ? borderManager.movingParticle() : borderManager.defaultParticle();
        Location centerLocation = center.toBukkit(world);

        /*
        * Keep the particle count bounded.
        *
        * Cuboids get 360 because those particles are divided across six faces.
        * Curved shapes get 420 because their sampler distributes points across
        * the complete surface.
        */
        int maxParticles = shape == BorderShape.CUBOID ? 360 : 420;
        List<Vector> points = BorderParticleSampler.sample(this, borderManager.particleSpacing(), maxParticles);

        double viewDistanceSquared = squared(borderManager.particleViewDistance());

        for (Player viewer : viewers) {
            Location viewerLocation = viewer.getLocation();
            int spawned = 0;

            for (Vector point : points) {
                Location location = centerLocation.clone().add(point);

                if (viewerLocation.distanceSquared(location) > viewDistanceSquared) {
                    continue;
                }

                viewer.spawnParticle(particle, location, 1, 0, 0, 0, 0, null, true);
                spawned++;
            }

            /*
            * Large ellipsoids/cylindroids can have globally distributed samples
            * with no sample landing inside this player's small view radius.
            *
            * In that case, render a small fallback patch around the surface
            * direction nearest to the player.
            */
            if (spawned == 0 && shape != BorderShape.CUBOID) {
                drawNearbySurfaceFallback(viewer, particle, centerLocation);
            }
        }
    }

    private void drawNearbySurfaceFallback(Player viewer, Particle particle, Location centerLocation) {
        Vector relative = viewer.getLocation().toVector().subtract(centerLocation.toVector());
        if (relative.lengthSquared() < 1.0e-6) {
            relative.setX(1);
        }
        Vector direction = relative.clone().normalize();
        Vector surface = findSurfacePoint(direction);
        if (surface == null) {
            return;
        }
        Location base = centerLocation.clone().add(surface);
        double maxDistanceSquared = squared(borderManager.particleViewDistance());
        if (viewer.getLocation().distanceSquared(base) > maxDistanceSquared) {
            return;
        }
        Vector tangentA = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (tangentA.lengthSquared() < 1.0e-6) {
            tangentA = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        tangentA.normalize();
        Vector tangentB = direction.clone().crossProduct(tangentA).normalize();
        double spacing = Math.max(1.25, borderManager.particleSpacing());
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                Vector sampleDirection = surface.clone()
                    .add(tangentA.clone().multiply(a * spacing))
                    .add(tangentB.clone().multiply(b * spacing))
                    .normalize();
                Vector sample = findSurfacePoint(sampleDirection);
                if (sample == null) {
                    continue;
                }
                Location location = centerLocation.clone().add(sample);
                if (viewer.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                    viewer.spawnParticle(particle, location, 1, 0, 0, 0, 0, null, true);
                }
            }
        }
    }
    private Vector findSurfacePoint(Vector direction) {
        Vector half = dimensions().multiply(0.5);
        double maxDistance = half.length() * 1.1;
        if (maxDistance <= 0) {
            return null;
        }
        double inside = 0;
        double outside = maxDistance;
        for (int i = 0; i < 10; i++) {
            double distance = (inside + outside) * 0.5;
            Vector point = direction.clone().multiply(distance);
            if (containsLocation(point.getX(), point.getY(), point.getZ())) {
                inside = distance;
            } else {
                outside = distance;
            }
        }
        return direction.clone().multiply(inside);
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
