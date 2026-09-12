package com.donutsforlife11.donutgame.internal.map;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapRotation;
import com.donutsforlife11.donutgame.internal.game.GameModule;
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
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;

public class WorldService {
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Plugin plugin;
    private final Set<String> ownedWorldNames = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> moduleIndexesByWorldName = new ConcurrentHashMap<>();
    private final Map<Path, CachedClipboard> clipboardCache = new ConcurrentHashMap<>();

    public WorldService(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<World> loadSlimeWorld(Path slimeFile, String worldName) {
        Objects.requireNonNull(slimeFile, "slimeFile");
        Objects.requireNonNull(worldName, "worldName");
        CompletableFuture<World> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SlimeLoader loader = new FileLoader(slimeFile.getParent().toFile());
                SlimeWorld template = asp.readWorld(loader, stripExtension(slimeFile.getFileName().toString()), true, new SlimePropertyMap());
                runSync(() -> {
                    if (Bukkit.getWorld(worldName) != null) {
                        throw new IllegalStateException("World " + worldName + " is already loaded.");
                    }
                    SlimeWorldInstance instance = asp.loadWorld(template.clone(worldName), true);
                    World world = instance.getBukkitWorld();
                    configureWorld(world);
                    ownedWorldNames.add(world.getName());
                    future.complete(world);
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
            CachedClipboard cached = loadClipboard(schematicFile);
            ClipboardHolder holder = new ClipboardHolder(cached.clipboard());
            if (rotation != MapRotation.DEG_0) {
                holder.setTransform(holder.getTransform().combine(new AffineTransform().rotateY(rotation.degrees())));
            }
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operations.complete(holder.createPaste(editSession)
                    .to(adjustedPastePosition(minCorner, rotation, cached.metadata()))
                    .ignoreAirBlocks(false)
                    .build());
            }
            future.complete(cached.metadata());
        }, future);
        return future;
    }

    public SchematicMetadata getSchematicMetadata(Path schematicFile) {
        return loadClipboard(schematicFile).metadata();
    }

    public void markWorldOwnedBy(GameModule module, World world) {
        if (module == null || world == null) {
            return;
        }
        moduleIndexesByWorldName.put(world.getName(), module.index());
    }

    public CompletableFuture<Void> unloadModuleWorlds(GameModule module) {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (GameWorld gameWorld : module.worlds().values()) {
            World world = gameWorld.bukkitWorld();
            if (world == null) {
                continue;
            }
            result = result.thenCompose(ignored -> unloadWorld(world.getName()).thenRun(() -> gameWorld.setBukkitWorld(null)));
        }
        return result;
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runSync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                ownedWorldNames.remove(worldName);
                moduleIndexesByWorldName.remove(worldName);
                tryDeleteWorldFolder(worldName);
                future.complete(null);
                return;
            }
            evacuatePlayers(world);
            removeNonPlayerEntities(world);
            if (!Bukkit.unloadWorld(world, false)) {
                throw new IllegalStateException("Bukkit refused to unload world " + worldName + ".");
            }
            ownedWorldNames.remove(worldName);
            moduleIndexesByWorldName.remove(worldName);
            tryDeleteWorldFolder(worldName);
            future.complete(null);
        }, future);
        return future;
    }

    public void unloadAll() {
        for (String worldName : List.copyOf(ownedWorldNames)) {
            try {
                unloadWorld(worldName).join();
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Failed to unload owned world " + worldName + ": " + exception.getMessage());
            }
        }
    }

    public List<String> loadedWorldNames() {
        return Bukkit.getWorlds().stream().map(world -> world.getName()).sorted().toList();
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

    private void runSync(ThrowingRunnable action, CompletableFuture<?> future) {
        Runnable task = () -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private BlockVector3 adjustedPastePosition(Location minCorner, MapRotation rotation, SchematicMetadata metadata) {
        int width = metadata.width();
        int depth = metadata.depth();
        int x = minCorner.getBlockX();
        int y = minCorner.getBlockY();
        int z = minCorner.getBlockZ();
        return switch (rotation) {
            case DEG_0 -> BlockVector3.at(x, y, z);
            case DEG_90 -> BlockVector3.at(x, y, z + width - 1);
            case DEG_180 -> BlockVector3.at(x + width - 1, y, z + depth - 1);
            case DEG_270 -> BlockVector3.at(x + depth - 1, y, z);
        };
    }

    private void evacuatePlayers(World world) {
        if (world.getPlayers().isEmpty()) {
            return;
        }
        Location destination = fallbackLocation(world);
        for (Player player : List.copyOf(world.getPlayers())) {
            player.closeInventory();
            player.teleport(destination);
        }
    }

    private Location fallbackLocation(World unloadingWorld) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(unloadingWorld)) {
                return world.getSpawnLocation();
            }
        }
        throw new IllegalStateException("Cannot unload world " + unloadingWorld.getName() + " because no destination world is loaded.");
    }

    private void removeNonPlayerEntities(World world) {
        for (Entity entity : List.copyOf(world.getEntities())) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private void tryDeleteWorldFolder(String worldName) {
        try {
            deleteWorldFolder(worldName);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unloaded world " + worldName + " but could not delete its folder: " + exception.getMessage());
        }
    }

    private void deleteWorldFolder(String worldName) throws IOException {
        Path worldFolder = Bukkit.getWorldContainer().toPath().resolve(worldName).normalize();
        if (!Files.exists(worldFolder)) {
            return;
        }
        try (var paths = Files.walk(worldFolder)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private CachedClipboard loadClipboard(Path schematicFile) {
        try {
            Path normalizedPath = schematicFile.toAbsolutePath().normalize();
            long lastModified = Files.getLastModifiedTime(normalizedPath).toMillis();
            CachedClipboard cached = clipboardCache.get(normalizedPath);
            if (cached != null && cached.lastModified() == lastModified) {
                return cached;
            }
            var format = ClipboardFormats.findByPath(normalizedPath);
            if (format == null) {
                throw new IllegalStateException("Unknown schematic format for " + normalizedPath);
            }
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(normalizedPath.toFile()))) {
                clipboard = reader.read();
            }
            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();
            clipboard.setOrigin(min);
            CachedClipboard loaded = new CachedClipboard(
                lastModified,
                clipboard,
                new SchematicMetadata(max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1)
            );
            clipboardCache.put(normalizedPath, loaded);
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load schematic " + schematicFile + ".", exception);
        }
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private record CachedClipboard(long lastModified, Clipboard clipboard, SchematicMetadata metadata) {
    }

    public record SchematicMetadata(int width, int height, int depth) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
