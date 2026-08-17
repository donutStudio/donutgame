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
    private boolean hungerEnabled = true;

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

    public void setWorldSpawn(GameLocation location) {
        requireLocation(location);
        bukkitWorld.setSpawnLocation(location.toBukkit(bukkitWorld));
    }

    public GameLocation worldSpawn() {
        return GameLocation.fromBukkit(bukkitWorld.getSpawnLocation());
    }

    public void setPvp(boolean enabled) {
        bukkitWorld.setGameRule(GameRules.PVP, enabled);
    }

    public void setFallDamage(boolean enabled) {
        bukkitWorld.setGameRule(GameRules.FALL_DAMAGE, enabled);
    }

    public void setHungerEnabled(boolean enabled) {
        hungerEnabled = enabled;
    }

    public boolean hungerEnabled() {
        return hungerEnabled;
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
        return matches == null ? new ArrayList<>() : new ArrayList<>(matches);
    }

    public GameLocation getPoint(String pointName) {
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
        return matches == null ? new ArrayList<>() : new ArrayList<>(matches);
    }

    public GameRegion getRegion(String regionName) {
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

    public void setBlock(double x, double y, double z, Material material) {
        setBlock(new GameLocation(x, y, z), material);
    }

    public void setBlock(GameLocation location, BlockData blockData) {
        requireLocation(location);
        bukkitWorld.getBlockAt(location.toBukkit(bukkitWorld)).setBlockData(blockData, false);
    }

    public void setBlock(double x, double y, double z, BlockData blockData) {
        setBlock(new GameLocation(x, y, z), blockData);
    }

    public CompletableFuture<Void> fill(double x0, double y0, double z0, double x1, double y1, double z1, Material material) {
        return fill(new GameLocation(x0, y0, z0), new GameLocation(x1, y1, z1), material);
    }

    public CompletableFuture<Void> fill(GameLocation pos1, GameLocation pos2, Material material) {
        return fill(new GameRegion(pos1, pos2), material);
    }

    public CompletableFuture<Void> fill(GameRegion region, Material material) {
        return fill(region, material.createBlockData());
    }

    public CompletableFuture<Void> fill(double x0, double y0, double z0, double x1, double y1, double z1, BlockData blockData) {
        return fill(new GameLocation(x0, y0, z0), new GameLocation(x1, y1, z1), blockData);
    }

    public CompletableFuture<Void> fill(GameLocation pos1, GameLocation pos2, BlockData blockData) {
        return fill(new GameRegion(pos1, pos2), blockData);
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

    public GameEntity summon(EntityType entityType, double x, double y, double z) {
        return summon(entityType, new GameLocation(x, y, z));
    }

    public GameChest newChest(GameLocation location) {
        setBlock(location, Material.CHEST);
        return new GameChest(this, location);
    }

    public GameChest newChest(double x, double y, double z) {
        return newChest(new GameLocation(x, y, z));
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
        points.forEach((name, entries) -> copy.put(name, new ArrayList<>(entries)));
        return copy;
    }

    public Map<String, List<GameRegion>> copyRegions() {
        Map<String, List<GameRegion>> copy = new LinkedHashMap<>();
        regions.forEach((name, entries) -> copy.put(name, new ArrayList<>(entries)));
        return copy;
    }

    private void requireRegion(GameRegion region) {
        if (region == null) throw new IllegalArgumentException("GameRegion cannot be null.");
    }

    private void requireLocation(GameLocation location) {
        if (location == null) throw new IllegalArgumentException("Location must be in the GameWorld.");
    }
}
