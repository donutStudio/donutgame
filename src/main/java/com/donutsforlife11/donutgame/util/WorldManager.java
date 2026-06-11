package com.donutsforlife11.donutgame.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.MapRotation;
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

import net.kyori.adventure.text.Component;

public class WorldManager {
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Donutgame plugin;
    private final Map<String, WorldSession> worldsByName = new ConcurrentHashMap<>();
    private final Map<Path, CachedClipboard> clipboardCache = new ConcurrentHashMap<>();

    public WorldManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<World> loadSlimeWorld(Path slimeFile, String templateWorldName, String instanceWorldName) {
        Objects.requireNonNull(slimeFile, "slimeFile");

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
                        UUID.randomUUID().toString(),
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

    public CompletableFuture<SchematicMetadata> pasteSchematic(Path schematicFile, World world, Location minimumCorner, MapRotation rotation) {
        Objects.requireNonNull(schematicFile, "schematicFile");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(minimumCorner, "minimumCorner");
        Objects.requireNonNull(rotation, "rotation");

        CompletableFuture<SchematicMetadata> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                CachedClipboard cachedClipboard = loadClipboard(schematicFile);
                ClipboardHolder holder = new ClipboardHolder(cachedClipboard.clipboard());

                if (rotation != MapRotation.DEG_0) {
                    holder.setTransform(holder.getTransform().combine(new AffineTransform().rotateY(rotation.degrees())));
                }

                BlockVector3 target = adjustedPastePosition(minimumCorner, rotation, cachedClipboard.metadata());
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
                        Math.ceil(box.getMaxX()) - 1,
                        Math.ceil(box.getMaxY()) - 1,
                        Math.ceil(box.getMaxZ()) - 1
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
                    for (Player player : world.getPlayers()) {
                        player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                    }

                    Bukkit.unloadWorld(world, false);
                }

                if (releasedSession != null) {
                    plugin.getPlayerStateStore().clearWorld(releasedSession.playerStateId());
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

    public void sendPlayerToWorld(Player player, World world) {
        if (world == null) {
            player.sendMessage(Component.text("World not found."));
            return;
        }

        player.teleportAsync(world.getSpawnLocation());
    }

    public String getPlayerStateId(World world) {
        WorldSession session = worldsByName.get(world.getName());
        return session == null ? world.getName() : session.playerStateId();
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

            BlockVector3 minimum = clipboard.getRegion().getMinimumPoint();
            BlockVector3 maximum = clipboard.getRegion().getMaximumPoint();
            clipboard.setOrigin(minimum);

            SchematicMetadata metadata = new SchematicMetadata(
                maximum.x() - minimum.x() + 1,
                maximum.y() - minimum.y() + 1,
                maximum.z() - minimum.z() + 1
            );

            CachedClipboard newClipboard = new CachedClipboard(lastModified, clipboard, metadata);
            clipboardCache.put(normalizedPath, newClipboard);
            return newClipboard;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schematic " + schematicFile, e);
        }
    }

    private BlockVector3 adjustedPastePosition(Location minimumCorner, MapRotation rotation, SchematicMetadata metadata) {
        int width = metadata.width();
        int depth = metadata.depth();

        int x = minimumCorner.getBlockX();
        int y = minimumCorner.getBlockY();
        int z = minimumCorner.getBlockZ();

        return switch (rotation) {
            case DEG_0 -> BlockVector3.at(x, y, z);
            case DEG_90 -> BlockVector3.at(x, y, z + (width - 1));
            case DEG_180 -> BlockVector3.at(x + (width - 1), y, z + (depth - 1));
            case DEG_270 -> BlockVector3.at(x + (depth - 1), y, z);
        };
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

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
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

    private record CachedClipboard(long lastModified, Clipboard clipboard, SchematicMetadata metadata) {
    }

    private record WorldSession(
        String playerStateId,
        SlimeWorldInstance worldInstance
    ) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public record SchematicMetadata(int width, int height, int depth) {
    }
}
