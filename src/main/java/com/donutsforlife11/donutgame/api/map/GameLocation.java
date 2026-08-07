package com.donutsforlife11.donutgame.api.map;

import org.bukkit.Location;
import org.bukkit.World;

public class GameLocation {
    private final double x;
    private final double y;
    private final double z;
    private final double pitch;
    private final double yaw;

    public GameLocation() {
        this(0, 0, 0, 0, 0);
    }

    public GameLocation(double x, double y, double z) {
        this(x, y, z, 0, 0);
    }

    public GameLocation(double x, double y, double z, double pitch, double yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public static GameLocation fromBukkit(Location location) {
        return new GameLocation(
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getPitch(),
            location.getYaw()
        );
    }

    public Location toBukkit(World world) {
        return new Location(world, x, y, z, (float) yaw, (float) pitch);
    }

    public double distanceSquared(GameLocation other) {
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        double deltaZ = z - other.z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
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

    public double pitch() {
        return pitch;
    }

    public double yaw() {
        return yaw;
    }
}
