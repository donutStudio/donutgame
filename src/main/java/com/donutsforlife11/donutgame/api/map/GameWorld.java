package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private final Map<String, List<GameLocation>> points = new LinkedHashMap<>();
    private final Map<String, List<GameRegion>> regions = new LinkedHashMap<>();
    private volatile World bukkitWorld;

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
        requireLocation(location);
        points.computeIfAbsent(pointName, key -> new ArrayList<>()).add(location);
    }

    public void removePoint(GameLocation location, String pointName) {
        requireLocation(location);
        List<GameLocation> matches = points.get(pointName);
        if (matches != null) matches.remove(location);
    }

    public void addRegion(GameRegion region, String regionName) {
        requireRegion(region);
        regions.computeIfAbsent(regionName, key -> new ArrayList<>()).add(region);
    }

    public void removeRegion(GameRegion region, String regionName) {
        requireRegion(region);
        List<GameRegion> matches = regions.get(regionName);
        if (matches != null) matches.remove(region);
    }

    public List<GameLocation> getPoints(String pointName) {
        List<GameLocation> matches = points.get(pointName);
        return matches == null ? List.of() : List.copyOf(matches);
    }

    public GameLocation point(String pointName) {
        List<GameLocation> matches = points.get(pointName);
        return matches == null || matches.isEmpty() ? null : matches.getFirst();
    }

    public double distanceToPoint(GameLocation location, String pointName) {
        List<GameLocation> matches = points.get(pointName);
        if (matches == null || matches.isEmpty()) return -1;
        double closest = Double.MAX_VALUE;
        for (GameLocation point : matches) closest = Math.min(closest, point.distanceSquared(location));
        return Math.sqrt(closest);
    }

    public List<GameRegion> getRegions(String regionName) {
        List<GameRegion> matches = regions.get(regionName);
        return matches == null ? List.of() : List.copyOf(matches);
    }

    public GameRegion region(String regionName) {
        List<GameRegion> matches = regions.get(regionName);
        return matches == null || matches.isEmpty() ? null : matches.getFirst();
    }

    public boolean posInRegion(GameLocation location, String regionName) {
        requireLocation(location);
        List<GameRegion> matches = regions.get(regionName);
        if (matches == null) return false;
        for (GameRegion region : matches) if (region.contains(location)) return true;
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
        requireLocation(location);
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
        requireLocation(location);
        Entity entity = bukkitWorld.spawnEntity(location.toBukkit(bukkitWorld), entityType);
        return new GameEntity(this, entity.getUniqueId(), entity.getType());
    }

    public GameChest placeChest(GameLocation location) {
        setBlock(location, Material.CHEST);
        return new GameChest(this, location);
    }

    public GameEntity entity(Entity entity) {
        return contains(entity) ? new GameEntity(this, entity.getUniqueId(), entity.getType()) : null;
    }

    public void replaceBukkitWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }

    public void clearMapData() {
        points.clear();
        regions.clear();
    }

    public Map<String, List<GameLocation>> copyPoints() {
        Map<String, List<GameLocation>> copy = new LinkedHashMap<>();
        points.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    public Map<String, List<GameRegion>> copyRegions() {
        Map<String, List<GameRegion>> copy = new LinkedHashMap<>();
        regions.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    private void requireRegion(GameRegion region) {
        if (region == null) throw new IllegalArgumentException("GameRegion cannot be null.");
    }

    private void requireLocation(GameLocation location) {
        if (location == null) throw new IllegalArgumentException("Location must be in the GameWorld.");
    }
}
