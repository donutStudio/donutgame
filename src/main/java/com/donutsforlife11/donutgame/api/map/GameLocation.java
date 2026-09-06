package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;

import org.bukkit.Location;

public class GameLocation {
    private final GameWorld world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public GameLocation(GameWorld world, Location location) {
        this(
            world,
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    public GameLocation(GameWorld world, double x, double y, double z) {
        this(world, x, y, z, 0, 0);
    }

    public GameLocation(GameWorld world, double x, double y, double z, float yaw, float pitch) {
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public GameWorld world() {
        return world;
    }

    public Location bukkitLocation() {
        return new Location(world.bukkitWorld(), x, y, z, yaw, pitch);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }
}
