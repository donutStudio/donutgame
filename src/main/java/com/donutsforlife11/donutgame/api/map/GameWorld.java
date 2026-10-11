package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.api.object.GameBlockType;
import com.donutsforlife11.donutgame.api.object.GameEntityType;

public class GameWorld {
    public static final long DEFAULT_WORLD_EDIT_BLOCK_LIMIT = 262_144L;

    private final String id;
    private final Map<String, List<GameLocation>> points = new LinkedHashMap<>();
    private final Map<String, List<GameRegion>> regions = new LinkedHashMap<>();
    private final List<Consumer<Boolean>> playerCollisionListeners = new ArrayList<>();
    private final GameWorldGamerules gamerules = new GameWorldGamerules(this);
    private World bukkitWorld;
    private long generation;
    private boolean hungerEnabled = true;
    private boolean playerCollisionsEnabled;
    private long worldEditBlockLimit = DEFAULT_WORLD_EDIT_BLOCK_LIMIT;

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

    World bukkitWorld() {
        return bukkitWorld;
    }

    void setBukkitWorld(World bukkitWorld) {
        if (this.bukkitWorld != bukkitWorld) {
            generation++;
        }
        this.bukkitWorld = bukkitWorld;
    }

    long generation() {
        return generation;
    }

    public GameLocation worldSpawn() {
        return new GameLocation(this, requireWorld().getSpawnLocation());
    }

    public void setWorldSpawn(GameLocation spawn) {
        requireLocation(spawn);
        requireWorld().setSpawnLocation(toBukkit(spawn));
    }

    public void setBlock(GameLocation location, Material material) {
        requireLocation(location);
        toBukkit(location).getBlock().setType(requireMaterial(material), false);
    }

    public void setBlock(GameLocation location, GameBlockType block) {
        requireBlock(block);
        setBlock(location, block.material());
    }

    public void setBlock(GameLocation location, BlockData blockData) {
        requireLocation(location);
        toBukkit(location).getBlock().setBlockData(requireBlockData(blockData), false);
    }

    public GameBlock blockAt(GameLocation location) {
        requireLocation(location);
        GameLocation scoped = location.world() == null ? location.inWorld(this) : location;
        return new GameBlock(this, generation, scoped.blockX(), scoped.blockY(), scoped.blockZ());
    }

    public void fill(GameRegion region, Material material) {
        fill(region, requireMaterial(material).createBlockData());
    }

    public void fill(GameRegion region, GameBlockType block) {
        requireBlock(block);
        fill(region, block.createBlockData());
    }

    public void fill(GameRegion region, BlockData blockData) {
        RegionBounds bounds = requireEditableRegion(region);
        BlockData data = requireBlockData(blockData);
        World world = requireWorld();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    world.getBlockAt(x, y, z).setBlockData(data.clone(), false);
                }
            }
        }
    }

    public void replace(GameRegion region, Material from, Material to) {
        replace(region, requireMaterial(from).createBlockData(), requireMaterial(to).createBlockData());
    }

    public void replace(GameRegion region, GameBlockType from, GameBlockType to) {
        requireBlock(from);
        requireBlock(to);
        replace(region, from.createBlockData(), to.createBlockData());
    }

    public void replace(GameRegion region, BlockData from, BlockData to) {
        RegionBounds bounds = requireEditableRegion(region);
        BlockData fromData = requireBlockData(from);
        BlockData toData = requireBlockData(to);
        World world = requireWorld();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getBlockData().equals(fromData)) {
                        block.setBlockData(toData.clone(), false);
                    }
                }
            }
        }
    }

    public GameEntity spawn(GameLocation location, EntityType type) {
        requireLocation(location);
        EntityType entityType = requireEntityType(type);
        Entity bukkitEntity = requireWorld().spawnEntity(toBukkit(location), entityType);
        return GameEntityHandle.create(this, generation, bukkitEntity);
    }

    public GameEntity spawn(GameLocation location, EntityType type, Consumer<? super GameEntity> configuration) {
        GameEntity entity = spawn(location, type);
        if (configuration != null) {
            configuration.accept(entity);
        }
        return entity;
    }

    public GameEntity spawn(GameLocation location, GameEntityType type) {
        requireEntity(type);
        return spawn(location, type.entityType());
    }

    public GameEntity spawn(GameLocation location, GameEntityType type, Consumer<? super GameEntity> configuration) {
        GameEntity entity = spawn(location, type);
        if (configuration != null) {
            configuration.accept(entity);
        }
        return entity;
    }

    public GameEntity summon(GameLocation location, EntityType type) {
        return spawn(location, type);
    }

    public GameEntity summon(GameLocation location, GameEntityType type) {
        return spawn(location, type);
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

    public boolean isChunkLoaded(int blockX, int blockZ) {
        World world = requireWorld();
        return world.isChunkLoaded(blockX >> 4, blockZ >> 4);
    }

    public int minHeight() {
        return requireWorld().getMinHeight();
    }

    public int maxHeight() {
        return requireWorld().getMaxHeight();
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

    public void setWorldEditBlockLimit(long blockLimit) {
        if (blockLimit <= 0) {
            throw new IllegalArgumentException("World edit block limit must be positive.");
        }
        worldEditBlockLimit = blockLimit;
    }

    public long worldEditBlockLimit() {
        return worldEditBlockLimit;
    }

    public void setPlayerCollisionsEnabled(boolean enabled) {
        playerCollisionsEnabled = enabled;
        World world = bukkitWorld;
        if (world != null) {
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                player.setCollidable(enabled);
            }
        }
        for (Consumer<Boolean> listener : List.copyOf(playerCollisionListeners)) {
            listener.accept(enabled);
        }
    }

    public boolean playerCollisionsEnabled() {
        return playerCollisionsEnabled;
    }

    public void onPlayerCollisionsChanged(Consumer<Boolean> listener) {
        if (listener != null) {
            playerCollisionListeners.add(listener);
        }
    }

    public void clearMapData() {
        points.clear();
        regions.clear();
    }

    Location toBukkit(GameLocation location) {
        return location.toBukkit(requireWorld());
    }

    World requireBukkitWorld() {
        return requireWorld();
    }

    World requireBukkitWorld(long handleGeneration) {
        requireCurrentGeneration(handleGeneration, "GameWorld");
        return requireWorld();
    }

    void requireCurrentGeneration(long handleGeneration, String handleType) {
        if (handleGeneration != generation) {
            throw new IllegalStateException(handleType + " handle for GameWorld " + id + " is stale.");
        }
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

    private RegionBounds requireEditableRegion(GameRegion region) {
        requireRegion(region);
        World world = requireWorld();
        RegionBounds bounds = new RegionBounds(
            region.min().blockX(),
            region.min().blockY(),
            region.min().blockZ(),
            region.max().blockX(),
            region.max().blockY(),
            region.max().blockZ()
        );
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()) {
            throw new IllegalArgumentException(
                "Region Y bounds " + bounds.minY() + ".." + bounds.maxY()
                    + " are outside world height " + world.getMinHeight() + ".." + (world.getMaxHeight() - 1) + "."
            );
        }
        long volume = bounds.volume();
        if (volume > worldEditBlockLimit) {
            throw new IllegalArgumentException(
                "World edit touches " + volume + " blocks, above the configured limit of " + worldEditBlockLimit + "."
            );
        }
        return bounds;
    }

    private void requireBlock(GameBlockType block) {
        if (block == null) {
            throw new IllegalArgumentException("block cannot be null");
        }
    }

    private Material requireMaterial(Material material) {
        if (material == null) {
            throw new IllegalArgumentException("material cannot be null");
        }
        return material;
    }

    private BlockData requireBlockData(BlockData blockData) {
        if (blockData == null) {
            throw new IllegalArgumentException("blockData cannot be null");
        }
        return blockData.clone();
    }

    private void requireEntity(GameEntityType entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
    }

    private EntityType requireEntityType(EntityType entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return entity;
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

    private record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume() {
            return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        }
    }
}
