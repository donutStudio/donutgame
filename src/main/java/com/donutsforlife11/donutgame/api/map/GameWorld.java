package com.donutsforlife11.donutgame.api.map;

import java.util.List;
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
    public void removePoint(Location location, String pointName) {
        requireWorld(location);
        points.get(pointName).remove(location);
    }
    public void addRegion(BoundingBox box, String regionName) {
        requireRegion(box);;
        regions.computeIfAbsent(regionName, ignored -> new CopyOnWriteArrayList<>()).add(box.clone());
    }
    public void removeRegion(BoundingBox box, String regionName) {
        requireRegion(box);
        regions.get(regionName).remove(box);
    }

    public List<Location> getPoints(String pointName) {
        return List.copyOf(points.get(pointName));
    }
    public double distanceToPoint(Location location, String pointName) {
        List<Location> locations = getPoints(pointName);
        if (locations.isEmpty()) {
            return -1;
        }
        double closestDistance = Double.MAX_VALUE;
        for (Location point : locations) {
            closestDistance = Math.min(closestDistance, point.distance(location));
        }
        return closestDistance;
    }
    public List<BoundingBox> getRegions(String regionName) {
        return List.copyOf(regions.get(regionName));
    }
    public boolean posInRegion(Location location, String regionName) {
        requireWorld(location);
        for (BoundingBox box : getRegions(regionName)) {
            if (box.contains(location.toVector())) {
                return true;
            }
        }
        return false;
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
