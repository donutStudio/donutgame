package com.donutsforlife11.donutgame.api.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class GameWorld {
    private final String name;
    private final WorldService worldService;
    private volatile World bukkitWorld;
    private final Map<String, CopyOnWriteArrayList<GameLocation>> points = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<GameRegion>> regions = new ConcurrentHashMap<>();

    public GameWorld(String name, World bukkitWorld, WorldService worldService) {
        this.name = name;
        this.bukkitWorld = bukkitWorld;
        this.worldService = worldService;
    }

    public World bukkitWorld() {
        return bukkitWorld;
    }

    public String name() {
        return name;
    }

    public GameLocation spawnLocation() {
        return GameLocation.fromBukkit(bukkitWorld.getSpawnLocation());
    }

    public void setPvp(boolean enabled) {
        bukkitWorld.setGameRule(GameRules.PVP, enabled);
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

    public GameLocation point(String pointName) {
        List<GameLocation> matches = getPoints(pointName);
        return matches.isEmpty() ? null : matches.getFirst();
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

    public GameRegion region(String regionName) {
        List<GameRegion> matches = getRegions(regionName);
        return matches.isEmpty() ? null : matches.getFirst();
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

    public GameChest placeChest(GameLocation location) {
        setBlock(location, Material.CHEST);
        return new GameChest(this, location);
    }

    public GameEntity entity(Entity entity) {
        if (!contains(entity)) {
            return null;
        }
        return new GameEntity(this, entity.getUniqueId(), entity.getType());
    }

    public void replaceBukkitWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }

    public void clearMapData() {
        points.clear();
        regions.clear();
    }

    public Map<String, List<GameLocation>> copyPoints() {
        Map<String, List<GameLocation>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, CopyOnWriteArrayList<GameLocation>> entry : points.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    public Map<String, List<GameRegion>> copyRegions() {
        Map<String, List<GameRegion>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, CopyOnWriteArrayList<GameRegion>> entry : regions.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
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
