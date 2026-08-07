package com.donutsforlife11.donutgame.api.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class GameWorld {
    private final World bukkitWorld;
    private final WorldService worldService;
    private final Map<String, CopyOnWriteArrayList<GameLocation>> points = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<GameRegion>> regions = new ConcurrentHashMap<>();

    public GameWorld(World bukkitWorld, WorldService worldService) {
        this.bukkitWorld = bukkitWorld;
        this.worldService = worldService;
    }

    public World bukkitWorld() {
        return bukkitWorld;
    }

    public GameLocation spawnLocation() {
        return GameLocation.fromBukkit(bukkitWorld.getSpawnLocation());
    }

    public void addPoint(GameLocation location, String pointName) {
        requireWorld(location);
        points.computeIfAbsent(pointName, ignored -> new CopyOnWriteArrayList<>()).add(location);
    }

    public void removePoint(GameLocation location, String pointName) {
        requireWorld(location);
        points.getOrDefault(pointName, new CopyOnWriteArrayList<>()).remove(location);
    }

    public void addRegion(GameRegion region, String regionName) {
        requireRegion(region);
        regions.computeIfAbsent(regionName, ignored -> new CopyOnWriteArrayList<>()).add(region);
    }

    public void removeRegion(GameRegion region, String regionName) {
        requireRegion(region);
        regions.getOrDefault(regionName, new CopyOnWriteArrayList<>()).remove(region);
    }

    public List<GameLocation> getPoints(String pointName) {
        return List.copyOf(points.getOrDefault(pointName, new CopyOnWriteArrayList<>()));
    }

    public double distanceToPoint(GameLocation location, String pointName) {
        List<GameLocation> locations = getPoints(pointName);
        if (locations.isEmpty()) {
            return -1;
        }
        double closestDistanceSquared = Double.MAX_VALUE;
        for (GameLocation point : locations) {
            closestDistanceSquared = Math.min(closestDistanceSquared, point.distanceSquared(location));
        }
        return Math.sqrt(closestDistanceSquared);
    }

    public List<GameRegion> getRegions(String regionName) {
        return List.copyOf(regions.getOrDefault(regionName, new CopyOnWriteArrayList<>()));
    }

    public boolean posInRegion(GameLocation location, String regionName) {
        requireWorld(location);
        for (GameRegion region : getRegions(regionName)) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(Location location) {
        return location != null && bukkitWorld.equals(location.getWorld());
    }

    public boolean contains(GameLocation location) {
        return location != null;
    }

    public boolean contains(Entity entity) {
        return entity != null && bukkitWorld.equals(entity.getWorld());
    }

    public void setBlock(GameLocation location, Material material) {
        setBlock(location, material.createBlockData());
    }

    public void setBlock(GameLocation location, BlockData blockData) {
        requireWorld(location);
        bukkitWorld.getBlockAt(location.toBukkit(bukkitWorld)).setBlockData(blockData, false);
    }

    public CompletableFuture<Void> fill(GameRegion region, Material material) {
        return fill(region, material.createBlockData());
    }

    public CompletableFuture<Void> fill(GameRegion region, BlockData blockData) {
        requireRegion(region);
        return worldService.fillArea(bukkitWorld, region.toBoundingBox(), blockData);
    }

    public GameEntity summon(EntityType entityType, GameLocation location) {
        requireWorld(location);
        Entity entity = bukkitWorld.spawnEntity(location.toBukkit(bukkitWorld), entityType);
        return new GameEntity(this, entity.getUniqueId(), entity.getType());
    }

    public GameEntity entity(Entity entity) {
        if (!contains(entity)) {
            return null;
        }
        return new GameEntity(this, entity.getUniqueId(), entity.getType());
    }

    private void requireRegion(GameRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("GameRegion cannot be null.");
        }
    }

    private void requireWorld(GameLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("Location must be in the GameWorld.");
        }
    }
}
