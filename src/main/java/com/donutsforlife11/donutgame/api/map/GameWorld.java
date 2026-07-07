package com.donutsforlife11.donutgame.api.map;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public class GameWorld {
    private World bukkitWorld;
    private final Map<String, CopyOnWriteArrayList<Location>> points = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<BoundingBox>> regions = new ConcurrentHashMap<>();

    public GameWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }

    public World getBukkitWorld() {
        return bukkitWorld;
    }

    public void addPoint(Location location, String pointName) {
        requireWorld(location);
        points.computeIfAbsent(pointName, ignored -> new CopyOnWriteArrayList<>()).add(location.clone());
    }
    public void addRegion(BoundingBox box, String pointName) {
        requireRegion(box);;
        regions.computeIfAbsent(pointName, ignored -> new CopyOnWriteArrayList<>()).add(box.clone());
    }

    private void requireRegion(BoundingBox box) {
        if (box == null) {
            throw new IllegalArgumentException("BoundingBox cannot be null.");
        }
    }
    private void requireWorld(Location location) {
        if (location == null || location.getWorld() == null || !bukkitWorld.equals(location.getWorld())) {
            throw new IllegalArgumentException("Location must be in the GameWorld.");
        }
    }
}
