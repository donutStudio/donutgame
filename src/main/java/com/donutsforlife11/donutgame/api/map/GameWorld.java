package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.object.BlockSpec;
import com.donutsforlife11.donutgame.api.object.EntitySpec;

public class GameWorld {
    private final String id;
    private final Map<String, List<GameLocation>> points = new LinkedHashMap<>();
    private final Map<String, List<GameRegion>> regions = new LinkedHashMap<>();
    private final GameWorldGamerules gamerules = new GameWorldGamerules(this);
    private World bukkitWorld;
    private boolean hungerEnabled = true;

    public GameWorld(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String name() {
        World world = bukkitWorld;
        return world == null ? id : world.getName();
    }

    public World bukkitWorld() { 
        return bukkitWorld;
    } // Will have to enforce encapsulation later so game modules can't access the bukkit worlds

    public void setBukkitWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }

    public GameLocation worldSpawn() {
        return new GameLocation(this, requireWorld().getSpawnLocation());
    }

    public void setWorldSpawn(GameLocation spawn) {
        requireLocation(spawn);
        requireWorld().setSpawnLocation(toBukkit(spawn));
    }

    public CompletableFuture<Void> setBlock(GameLocation location, BlockSpec<?> block) {
        requireLocation(location);
        requireBlock(block);
        block.applyTo(toBukkit(location).getBlock());
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> fillBlocks(GameRegion region, BlockSpec<?> block) {
        requireRegion(region);
        requireBlock(block);
        World world = requireWorld();
        int minX = region.min().blockX();
        int minY = region.min().blockY();
        int minZ = region.min().blockZ();
        int maxX = region.max().blockX();
        int maxY = region.max().blockY();
        int maxZ = region.max().blockZ();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    block.applyTo(world.getBlockAt(x, y, z));
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> replaceBlocks(GameRegion region, BlockSpec<?> from, BlockSpec<?> to) {
        requireRegion(region);
        requireBlock(from);
        requireBlock(to);
        World world = requireWorld();
        int minX = region.min().blockX();
        int minY = region.min().blockY();
        int minZ = region.min().blockZ();
        int maxX = region.max().blockX();
        int maxY = region.max().blockY();
        int maxZ = region.max().blockZ();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (from.matches(block)) {
                        to.applyTo(block);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> summon(EntitySpec<?> entity, GameLocation location) {
        summonEntity(location, entity);
        return CompletableFuture.completedFuture(null);
    }

    public GameEntity summonEntity(GameLocation location, EntitySpec<?> entity) {
        requireLocation(location);
        requireEntity(entity);
        World world = requireWorld();
        Entity bukkitEntity = entity.spawn(world, toBukkit(location));
        return new GameEntity(this, bukkitEntity);
    }

    public CompletableFuture<Void> fill(GameRegion region, BlockSpec<?> block) {
        return fillBlocks(region, block);
    }

    public GameEntity summon(GameLocation location, EntitySpec<?> entity) {
        return summonEntity(location, entity);
    }

    public void addPoint(String name, GameLocation location) {
        requireName(name);
        requireLocation(location);
        points.computeIfAbsent(name, ignored -> new ArrayList<>()).add(location.inWorld(this));
    }

    public void removePoint(String name, GameLocation location) {
        requireName(name);
        requireLocation(location);
        List<GameLocation> matches = points.get(name);
        if (matches != null) {
            matches.removeIf(existing -> sameBlock(existing, location));
        }
    }

    public List<GameLocation> getPoints(String name) {
        requireName(name);
        return points.getOrDefault(name, List.of()).stream().map(location -> location.inWorld(this)).toList();
    }

    public Map<String, List<GameLocation>> getPoints() {
        Map<String, List<GameLocation>> copy = new LinkedHashMap<>();
        points.forEach((name, entries) -> copy.put(name, entries.stream().map(location -> location.inWorld(this)).toList()));
        return Map.copyOf(copy);
    }

    public GameLocation getPoint(String name) {
        List<GameLocation> matches = getPoints(name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public void addRegion(String name, GameRegion region) {
        requireName(name);
        requireRegion(region);
        regions.computeIfAbsent(name, ignored -> new ArrayList<>()).add(region);
    }

    public void removeRegion(String name, GameRegion region) {
        requireName(name);
        requireRegion(region);
        List<GameRegion> matches = regions.get(name);
        if (matches != null) {
            matches.removeIf(existing -> sameBlock(existing.min(), region.min()) && sameBlock(existing.max(), region.max()));
        }
    }

    public List<GameRegion> getRegions(String name) {
        requireName(name);
        return List.copyOf(regions.getOrDefault(name, List.of()));
    }

    public Map<String, List<GameRegion>> getRegions() {
        Map<String, List<GameRegion>> copy = new LinkedHashMap<>();
        regions.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    public GameRegion getRegion(String name) {
        List<GameRegion> matches = getRegions(name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public boolean regionContainsPos(String regionName, GameLocation location) {
        requireLocation(location);
        return getRegions(regionName).stream().anyMatch(region -> region.contains(location));
    }

    public GameWorldGamerules gamerules() {
        return gamerules;
    }

    public void setTime(long time) {
        requireWorld().setTime(time);
    }

    public long time() {
        return requireWorld().getTime();
    }

    public void setWeather(GameWeather weather) {
        if (weather == null) {
            throw new IllegalArgumentException("weather cannot be null");
        }
        World world = requireWorld();
        switch (weather) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }
    }

    public GameWeather weather() {
        World world = requireWorld();
        if (world.isThundering()) {
            return GameWeather.THUNDER;
        }
        return world.hasStorm() ? GameWeather.RAIN : GameWeather.CLEAR;
    }

    public void setDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            throw new IllegalArgumentException("difficulty cannot be null");
        }
        requireWorld().setDifficulty(difficulty);
    }

    public Difficulty difficulty() {
        return requireWorld().getDifficulty();
    }

    public void setHungerEnabled(boolean enabled) {
        hungerEnabled = enabled;
    }

    public boolean hungerEnabled() {
        return hungerEnabled;
    }

    public void clearMapData() {
        points.clear();
        regions.clear();
    }

    private Location toBukkit(GameLocation location) {
        return location.toBukkit(requireWorld());
    }

    private World requireWorld() {
        if (bukkitWorld == null) {
            throw new IllegalStateException("GameWorld " + id + " is not loaded.");
        }
        return bukkitWorld;
    }

    private void requireLocation(GameLocation location) {
        if (location == null || (location.world() != null && location.world() != this)) {
            throw new IllegalArgumentException("Location must be in GameWorld " + id + ".");
        }
    }

    private void requireRegion(GameRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("region cannot be null");
        }
        if (region.world() != null && region.world() != this) {
            throw new IllegalArgumentException("Region must be in GameWorld " + id + ".");
        }
    }

    private void requireBlock(BlockSpec<?> block) {
        if (block == null) {
            throw new IllegalArgumentException("block cannot be null");
        }
    }

    private void requireEntity(EntitySpec<?> entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    private boolean sameBlock(GameLocation left, GameLocation right) {
        return left.blockX() == right.blockX()
            && left.blockY() == right.blockY()
            && left.blockZ() == right.blockZ();
    }
}
