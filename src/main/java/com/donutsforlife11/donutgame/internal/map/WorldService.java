package com.donutsforlife11.donutgame.internal.map;

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
import com.donutsforlife11.donutgame.internal.player.PlayerWorldStateService;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
public class WorldService {
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final Plugin plugin;
    private final PlayerWorldStateService playerWorldStateService;
    private final Set<String> ownedWorldNames = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> moduleIndexesByWorldName = new ConcurrentHashMap<>();
    private final SyncExecutor syncExecutor = this::runSync;
    private volatile SchematicService schematicService;

    public WorldService(Plugin plugin, PlayerWorldStateService playerWorldStateService) {
        this.plugin = plugin;
        this.playerWorldStateService = playerWorldStateService;
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
                    playerWorldStateService.markIsolatedWorld(world);
                    future.complete(world);
                }, future);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<SchematicMetadata> pasteSchematic(Path schematicFile, World world, Location minCorner, MapRotation rotation) {
        return schematicService().paste(schematicFile, world, minCorner, rotation);
    }

    public SchematicMetadata getSchematicMetadata(Path schematicFile) {
        return schematicService().metadata(schematicFile);
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
            boolean owned = ownedWorldNames.contains(worldName) || moduleIndexesByWorldName.containsKey(worldName);
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                if (owned) {
                    ownedWorldNames.remove(worldName);
                    moduleIndexesByWorldName.remove(worldName);
                    tryDeleteWorldFolder(worldName);
                }
                future.complete(null);
                return;
            }
            if (!owned) {
                throw new IllegalArgumentException("Refusing to unload unowned world " + worldName + ".");
            }
            evacuatePlayers(world);
            removeNonPlayerEntities(world);
            if (!Bukkit.unloadWorld(world, false)) {
                throw new IllegalStateException("Bukkit refused to unload world " + worldName + ".");
            }
            playerWorldStateService.forgetWorld(world);
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
        ownedWorldNames.clear();
        moduleIndexesByWorldName.clear();
        clearSchematicCache();
    }

    public List<String> loadedWorldNames() {
        return Bukkit.getWorlds().stream().map(world -> world.getName()).sorted().toList();
    }

    private void configureWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.PVP, true);
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

    private void evacuatePlayers(World world) {
        if (world.getPlayers().isEmpty()) {
            return;
        }
        Location destination = fallbackLocation(world);
        for (Player player : List.copyOf(world.getPlayers())) {
            player.closeInventory();
            if (!player.teleport(destination)) {
                throw new IllegalStateException("Failed to evacuate " + player.getName() + " from world " + world.getName() + ".");
            }
        }
        if (!world.getPlayers().isEmpty()) {
            throw new IllegalStateException("Cannot unload world " + world.getName() + " because players remain in it.");
        }
    }

    private Location fallbackLocation(World unloadingWorld) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(unloadingWorld) && !ownedWorldNames.contains(world.getName())) {
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
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldFolder = worldContainer.resolve(worldName).normalize();
        if (!worldFolder.startsWith(worldContainer)) {
            throw new IOException("Refusing to delete world folder outside the server world container: " + worldName);
        }
        if (!Files.exists(worldFolder)) {
            return;
        }
        try (var paths = Files.walk(worldFolder)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private SchematicService schematicService() {
        SchematicService service = schematicService;
        if (service != null) {
            return service;
        }
        synchronized (this) {
            if (schematicService != null) {
                return schematicService;
            }
            try {
                schematicService = new WorldEditSchematicService(syncExecutor);
            } catch (LinkageError | RuntimeException exception) {
                plugin.getLogger().warning("Schematic support is disabled: " + exception.getMessage());
                schematicService = new UnavailableSchematicService(exception);
            }
            return schematicService;
        }
    }

    private void clearSchematicCache() {
        SchematicService service = schematicService;
        if (service != null) {
            service.clearCache();
        }
    }

    public record SchematicMetadata(int width, int height, int depth) {
    }

    @FunctionalInterface
    interface SyncExecutor {
        void runSync(ThrowingRunnable action, CompletableFuture<?> future);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
