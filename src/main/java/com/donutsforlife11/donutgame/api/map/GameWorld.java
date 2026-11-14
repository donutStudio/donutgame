package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.api.object.GameBlockType;
import com.donutsforlife11.donutgame.api.object.GameEntityType;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;

public final class GameWorld extends PaperWorldMethods {
    public static final long DEFAULT_WORLD_EDIT_BLOCK_LIMIT = 262_144L;

    private final GameModule owner;
    private final Map<String, List<GameLocation>> points = new LinkedHashMap<>();
    private final Map<String, List<GameRegion>> regions = new LinkedHashMap<>();
    private final List<Consumer<Boolean>> playerCollisionListeners = new ArrayList<>();
    private final GameWorldGamerules gamerules = new GameWorldGamerules(this);
    private World bukkitWorld;
    private long generation;
    private boolean hungerEnabled = true;
    private boolean playerCollisionsEnabled;
    private long worldEditBlockLimit = DEFAULT_WORLD_EDIT_BLOCK_LIMIT;

    public GameWorld(GameModule owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public GameModule owner() {
        return owner;
    }

    public String name() {
        World world = bukkitWorld;
        return world == null ? owner.id() : world.getName();
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

    public GameLocation getSpawnLocation() {
        return new GameLocation(this, requireWorld().getSpawnLocation());
    }

    public void setSpawnLocation(GameLocation spawn) {
        Location target = toBukkit(spawn);
        target.getWorld().setSpawnLocation(target);
    }

    public void setBlock(GameLocation location, Material material) {
        toBukkit(location).getBlock().setType(Objects.requireNonNull(material, "material"), false);
    }

    public void setBlock(GameLocation location, GameBlockType block) {
        Objects.requireNonNull(block, "block");
        setBlock(location, block.material());
    }

    public void setBlock(GameLocation location, BlockData blockData) {
        toBukkit(location).getBlock().setBlockData(Objects.requireNonNull(blockData, "blockData").clone(), false);
    }

    public GameBlock blockAt(GameLocation location) {
        requireLocation(location);
        requireWorld();
        return new GameBlock(this, generation, location.blockX(), location.blockY(), location.blockZ());
    }

    public void fill(GameRegion region, Material material) {
        fill(region, Objects.requireNonNull(material, "material").createBlockData());
    }

    public void fill(GameRegion region, GameBlockType block) {
        Objects.requireNonNull(block, "block");
        fill(region, block.createBlockData());
    }

    public void fill(GameRegion region, BlockData blockData) {
        RegionBounds bounds = requireEditableRegion(region);
        BlockData data = Objects.requireNonNull(blockData, "blockData").clone();
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
        replace(region, Objects.requireNonNull(from, "from").createBlockData(), Objects.requireNonNull(to, "to").createBlockData());
    }

    public void replace(GameRegion region, GameBlockType from, GameBlockType to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        replace(region, from.createBlockData(), to.createBlockData());
    }

    public void replace(GameRegion region, BlockData from, BlockData to) {
        RegionBounds bounds = requireEditableRegion(region);
        BlockData fromData = Objects.requireNonNull(from, "from").clone();
        BlockData toData = Objects.requireNonNull(to, "to").clone();
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

    public GameEntity summon(GameLocation location, GameEntityType type) {
        Location target = toBukkit(location);
        Entity entity = target.getWorld().spawnEntity(target, Objects.requireNonNull(type, "type").entityType());
        return GameEntityHandle.create(this, entity);
    }

    public GameNpc newNpc(GameLocation location) {
        return newNpc(location, (PlayerProfile) null);
    }

    public GameNpc newNpc(GameLocation location, PlayerProfile profile) {
        Location target = toBukkit(location);
        Mannequin mannequin = target.getWorld().spawn(target, Mannequin.class);
        try {
            return JavaPlugin.getPlugin(Donutgame.class).npcService().register(this, mannequin, profile);
        } catch (RuntimeException exception) {
            mannequin.remove();
            throw exception;
        }
    }

    public GameNpc newNpc(GameLocation location, GamePlayer player) {
        GameScope.requirePlayer(owner, Objects.requireNonNull(player, "player"));
        return newNpc(location, Objects.requireNonNull(player.playerProfile(), "Player must be online"));
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
        return List.copyOf(points.getOrDefault(name, List.of()));
    }

    public Map<String, List<GameLocation>> getPoints() {
        Map<String, List<GameLocation>> copy = new LinkedHashMap<>();
        points.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    public GameLocation getPoint(String name) {
        List<GameLocation> matches = getPoints(name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public void addRegion(String name, GameRegion region) {
        requireName(name);
        requireRegion(region);
        regions.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new GameRegion(region.min().inWorld(this), region.max().inWorld(this)));
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

    public GameWorldGamerules gamerules() {
        return gamerules;
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
        GameScope.requireActive(owner);
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
        requireLocation(location);
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
            throw new IllegalStateException(handleType + " handle for GameWorld " + name() + " is stale.");
        }
    }

    World requireWorld() {
        if (bukkitWorld == null || !owner.acceptsLiveOperations()) {
            throw new IllegalStateException("GameWorld " + name() + " is not loaded.");
        }
        return bukkitWorld;
    }

    private void requireLocation(GameLocation location) {
        GameScope.requireLocation(this, location);
    }

    private void requireRegion(GameRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("region cannot be null");
        }
        requireLocation(region.min());
        requireLocation(region.max());
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
