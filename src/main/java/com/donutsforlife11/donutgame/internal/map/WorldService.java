package com.donutsforlife11.donutgame.internal.map;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import com.donutsforlife11.donutgame.api.map.MapRotation;
import com.donutsforlife11.donutgame.internal.player.PlayerStateStore;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;

public class WorldService {
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Plugin plugin;
    private final PlayerStateStore playerStateStore;
    private final Map<String, WorldSession> worldsByName = new ConcurrentHashMap<>();
    private final Map<Path, CachedClipboard> clipboardCache = new ConcurrentHashMap<>();

    public WorldService(Plugin plugin, PlayerStateStore playerStateStore) {
        this.plugin = plugin;
        this.playerStateStore = playerStateStore;
    }

    public CompletableFuture<World> loadSlimeWorld(Path slimeFile, String instanceWorldName, String playerStateId) {
        Objects.requireNonNull(slimeFile, "Slime world can't be null!");
        CompletableFuture<World> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SlimeLoader loader = new FileLoader(slimeFile.getParent().toFile());
                SlimePropertyMap properties = new SlimePropertyMap();
                String storedWorldName = stripExtension(slimeFile.getFileName().toString());
                SlimeWorld template = asp.readWorld(loader, storedWorldName, true, properties);
                SlimeWorld instanceWorld = template.clone(instanceWorldName);
                runSync(() -> {
                    SlimeWorldInstance instance = asp.loadWorld(instanceWorld, true);
                    World world = instance.getBukkitWorld();
                    configureWorld(world);

                    worldsByName.put(world.getName(), new WorldSession(
                        playerStateId,
                        instance
                    ));

                    future.complete(world);
                    return null;
                }, future);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<SchematicMetadata> pasteSchematic(Path schematicFile, World world, Location minCorner, MapRotation rotation) {
        Objects.requireNonNull(schematicFile, "schematicFile");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(minCorner, "minCorner");
        Objects.requireNonNull(rotation, "rotation");

        CompletableFuture<SchematicMetadata> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                CachedClipboard cachedClipboard = loadClipboard(schematicFile);
                ClipboardHolder holder = new ClipboardHolder(cachedClipboard.clipboard());

                if (rotation != MapRotation.DEG_0) {
                    holder.setTransform(holder.getTransform().combine(new AffineTransform().rotateY(rotation.degrees())));
                }

                BlockVector3 target = adjustedPastePosition(minCorner, rotation, cachedClipboard.metadata());
                try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                    Operation operation = holder.createPaste(editSession)
                        .to(target)
                        .ignoreAirBlocks(false)
                        .build();
                    Operations.complete(operation);
                    // editSession.flushSession();
                }
                future.complete(cachedClipboard.metadata());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            return null;
        }, future);
        return future;
    }

    public SchematicMetadata getSchematicMetadata(Path schematicFile) {
        return loadClipboard(schematicFile).metadata();
    }
    public CompletableFuture<Void> fillArea(World world, BoundingBox box, BlockData blockData) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(blockData, "blockData");

        CompletableFuture<Void> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                BlockVector3 min = BlockVector3.at(
                    Math.floor(box.getMinX()),
                    Math.floor(box.getMinY()),
                    Math.floor(box.getMinZ())
                );

                BlockVector3 max = BlockVector3.at(
                    Math.ceil(box.getMaxX()),
                    Math.ceil(box.getMaxY()),
                    Math.ceil(box.getMaxZ())
                );

                com.sk89q.worldedit.world.block.BlockState worldEditBlock =
                    BukkitAdapter.adapt(blockData);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                    editSession.setBlocks(new CuboidRegion(min, max), worldEditBlock);
                }

                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            return null;
        }, future);

        return future;
    }
    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                WorldSession releasedSession = worldsByName.remove(worldName);
                World world = Bukkit.getWorld(worldName);

                if (world != null) {
                    Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
                    for (Player player : world.getPlayers()) {
                        player.teleport(fallback);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            Bukkit.unloadWorld(world, false);
                            deleteWorldFolder(worldName);
                            future.complete(null);
                        } catch (Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }
                    });
                    return null;
                }

                if (releasedSession != null) {
                    deleteWorldFolder(worldName);
                }
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            return null;
        }, future);

        return future;
    }
    public String getPlayerStateId(World world) {
        WorldSession session = worldsByName.get(world.getName());
        return session == null ? world.getName() : session.playerStateId();
    }

    public void clearPlayerState(String playerStateId) {
        playerStateStore.clearWorld(playerStateId);
    }

    public void unloadAll() {
        for (SlimeWorldInstance instance : asp.getLoadedWorlds()) {
            try {
                asp.saveWorld(instance);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Bukkit.unloadWorld(instance.getBukkitWorld(), false);
        }
    }

    private void configureWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
        world.setGameRule(GameRules.RESPAWN_RADIUS, 0);
        world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        world.setDifficulty(Difficulty.HARD);
        world.setTime(1000);
    }
    private <T> void runSync(ThrowingSupplier<T> action, CompletableFuture<?> future) {
        Runnable task = () -> {
            try {
                action.get();
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }
    private BlockVector3 adjustedPastePosition(Location minCorner, MapRotation rotation, SchematicMetadata metadata) {
        int width = metadata.width();
        int depth = metadata.depth();

        int x = minCorner.getBlockX();
        int y = minCorner.getBlockY();
        int z = minCorner.getBlockZ();

        return switch (rotation) {
            case DEG_0 -> BlockVector3.at(x, y, z);
            case DEG_90 -> BlockVector3.at(x, y, z + (width - 1));
            case DEG_180 -> BlockVector3.at(x + (width - 1), y, z + (depth - 1));
            case DEG_270 -> BlockVector3.at(x + (depth - 1), y, z);
        };
    }
    private void deleteWorldFolder(String worldName) throws IOException {
        Path worldFolder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        deleteRecursively(worldFolder);
    }
    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            for (Path currentPath : walk.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(currentPath);
            }
        }
    }
    private CachedClipboard loadClipboard(Path schematicFile) {
        try {
            Path normalizedPath = schematicFile.toAbsolutePath().normalize();
            long lastModified = java.nio.file.Files.getLastModifiedTime(normalizedPath).toMillis();
            CachedClipboard cachedClipboard = clipboardCache.get(normalizedPath);

            if (cachedClipboard != null && cachedClipboard.lastModified() == lastModified) {
                return cachedClipboard;
            }

            Clipboard clipboard;
            var format = ClipboardFormats.findByFile(normalizedPath.toFile());
            if (format == null) {
                throw new IllegalStateException("Unknown schematic format for " + normalizedPath);
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(normalizedPath.toFile()))) {
                clipboard = reader.read();
            }

            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();
            clipboard.setOrigin(min);

            SchematicMetadata metadata = new SchematicMetadata(
                max.x() - min.x() + 1,
                max.y() - min.y() + 1,
                max.z() - min.z() + 1
            );

            CachedClipboard newClipboard = new CachedClipboard(lastModified, clipboard, metadata);
            clipboardCache.put(normalizedPath, newClipboard);
            return newClipboard;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schematic " + schematicFile, e);
        }
    }
    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private record CachedClipboard(long lastModified, Clipboard clipboard, SchematicMetadata metadata) {
    }
    private record WorldSession(String playerStateId, SlimeWorldInstance worldInstance) {
    }
    public record SchematicMetadata(int width, int height, int depth) {
    }
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
