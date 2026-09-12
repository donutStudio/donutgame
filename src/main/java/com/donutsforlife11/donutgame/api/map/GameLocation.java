package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;

public class GameLocation {
    private final GameWorld world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public GameLocation() {
        this(null, 0, 0, 0, 0, 0);
    }

    public GameLocation(double x, double y, double z) {
        this(null, x, y, z, 0, 0);
    }

    public GameLocation(double x, double y, double z, double yaw, double pitch) {
        this(null, x, y, z, (float) yaw, (float) pitch);
    }

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
        this.world = world;
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
        if (world == null) {
            throw new IllegalStateException("Location is not bound to a GameWorld.");
        }
        return toBukkit(world.bukkitWorld());
    }

    public Location toBukkit(World world) {
        Objects.requireNonNull(world, "world");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public GameLocation inWorld(GameWorld world) {
        return new GameLocation(Objects.requireNonNull(world, "world"), x, y, z, yaw, pitch);
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
