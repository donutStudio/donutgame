package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

public final class GameWorld {
    private final World bukkitWorld;
    private final Map<String, CopyOnWriteArrayList<Location>> points = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<BoundingBox>> regions = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<Entity>>> enterActions = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<Entity>>> exitActions = new ConcurrentHashMap<>();
    private final RegionFiller regionFiller;

    public GameWorld(World bukkitWorld, RegionFiller regionFiller) {
        this.bukkitWorld = Objects.requireNonNull(bukkitWorld, "bukkitWorld");
        this.regionFiller = Objects.requireNonNull(regionFiller, "regionFiller");
    }

    public World getBukkitWorld() {
        return bukkitWorld;
    }

    public List<Location> getPoints(String pointName) {
        return cloneLocations(points.getOrDefault(pointName, new CopyOnWriteArrayList<>()));
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

    public void addPoint(Location location, String pointName) {
        requireWorld(location);
        points.computeIfAbsent(pointName, ignored -> new CopyOnWriteArrayList<>()).add(location.clone());
    }

    public int removePoint(Location location, String pointName) {
        requireWorld(location);
        CopyOnWriteArrayList<Location> locations = points.get(pointName);

        if (locations == null) {
            return 0;
        }

        int removed = 0;
        for (Location point : List.copyOf(locations)) {
            if (sameBlock(point, location)) {
                if (locations.remove(point)) {
                    removed++;
                }
            }
        }

        return removed;
    }

    public List<BoundingBox> getRegions(String regionName) {
        return List.copyOf(regions.getOrDefault(regionName, new CopyOnWriteArrayList<>()));
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

    public void addRegion(BoundingBox box, String regionName) {
        requireRegion(box);
        regions.computeIfAbsent(regionName, ignored -> new CopyOnWriteArrayList<>()).add(box.clone());
    }

    public int removeRegion(BoundingBox box, String regionName) {
        requireRegion(box);
        CopyOnWriteArrayList<BoundingBox> boxes = regions.get(regionName);

        if (boxes == null) {
            return 0;
        }

        int removed = 0;
        for (BoundingBox currentBox : List.copyOf(boxes)) {
            if (sameBox(currentBox, box)) {
                if (boxes.remove(currentBox)) {
                    removed++;
                }
            }
        }

        return removed;
    }

    public void onEntityEnterRegion(String regionName, Consumer<Entity> action) {
        enterActions.computeIfAbsent(regionName, ignored -> new CopyOnWriteArrayList<>()).add(action);
    }

    public void onEntityExitRegion(String regionName, Consumer<Entity> action) {
        exitActions.computeIfAbsent(regionName, ignored -> new CopyOnWriteArrayList<>()).add(action);
    }

    public CompletableFuture<Void> fillArea(BoundingBox box, Material material) {
        Objects.requireNonNull(material, "material");
        return fillArea(box, material.createBlockData());
    }

    public CompletableFuture<Void> fillArea(BoundingBox box, BlockData blockData) {
        requireRegion(box);
        Objects.requireNonNull(blockData, "blockData");
        return regionFiller.fill(bukkitWorld, box, blockData);
    }

    public void handleRegionCheck(Entity entity, Location from, Location to) {
        if (entity == null || to == null || !bukkitWorld.equals(to.getWorld())) {
            return;
        }

        Collection<String> trackedNames = trackedRegionNames();
        if (trackedNames.isEmpty()) {
            return;
        }

        for (String regionName : trackedNames) {
            boolean wasInside = from != null && bukkitWorld.equals(from.getWorld()) && posInRegion(from, regionName);
            boolean isInside = posInRegion(to, regionName);

            if (wasInside == isInside) {
                continue;
            }

            if (isInside) {
                runActions(enterActions.get(regionName), entity);
            } else {
                runActions(exitActions.get(regionName), entity);
            }
        }
    }

    private Collection<String> trackedRegionNames() {
        LinkedHashSet<String> trackedNames = new LinkedHashSet<>(enterActions.keySet());
        trackedNames.addAll(exitActions.keySet());
        return trackedNames;
    }

    private List<Location> cloneLocations(Collection<Location> locations) {
        List<Location> clones = new ArrayList<>(locations.size());
        for (Location location : locations) {
            clones.add(location.clone());
        }
        return List.copyOf(clones);
    }

    private void runActions(List<Consumer<Entity>> actions, Entity entity) {
        if (actions == null) {
            return;
        }

        for (Consumer<Entity> action : actions) {
            action.accept(entity);
        }
    }

    private void requireWorld(Location location) {
        if (location == null || location.getWorld() == null || !bukkitWorld.equals(location.getWorld())) {
            throw new IllegalArgumentException("Location must be in the GameWorld.");
        }
    }

    private void requireRegion(BoundingBox box) {
        if (box == null) {
            throw new IllegalArgumentException("BoundingBox cannot be null.");
        }
    }

    private boolean sameBlock(Location left, Location right) {
        return left.getBlockX() == right.getBlockX()
            && left.getBlockY() == right.getBlockY()
            && left.getBlockZ() == right.getBlockZ();
    }

    private boolean sameBox(BoundingBox left, BoundingBox right) {
        return left.getMinX() == right.getMinX()
            && left.getMinY() == right.getMinY()
            && left.getMinZ() == right.getMinZ()
            && left.getMaxX() == right.getMaxX()
            && left.getMaxY() == right.getMaxY()
            && left.getMaxZ() == right.getMaxZ();
    }

    @FunctionalInterface
    public interface RegionFiller {
        CompletableFuture<Void> fill(World world, BoundingBox box, BlockData blockData);
    }
}
