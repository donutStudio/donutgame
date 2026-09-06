package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class MapManager {
    private final GameModule module;
    private final MapService mapService;
    private final WorldService worldService;
    private volatile GameMapDescriptor currentMap;
    private volatile CompletableFuture<GameMapDescriptor> pendingMapLoad;

    public MapManager(GameModule module, MapService mapService, WorldService worldService) {
        this.module = module;
        this.mapService = mapService;
        this.worldService = worldService;
    }

    public CompletableFuture<GameMapDescriptor> setMap(String mapId) {
        Objects.requireNonNull(mapId, "mapId");
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(mapId);
        CompletableFuture<GameMapDescriptor> future = loadMap(descriptor);
        pendingMapLoad = future;
        return future;
    }

    public CompletableFuture<GameMapDescriptor> whenReady() {
        CompletableFuture<GameMapDescriptor> future = pendingMapLoad;
        if (future != null) {
            return future;
        }
        GameMapDescriptor map = currentMap;
        return map == null
            ? CompletableFuture.failedFuture(new IllegalStateException("Module did not set a map in beforeLoad(). Configure 'map', configure 'maps', or call mapManager().setMap(...)."))
            : CompletableFuture.completedFuture(map);
    }

    public CompletableFuture<Void> unloadWorlds() {
        return worldService.unloadModuleWorlds(module);
    }

    public GameMapDescriptor map() {
        return currentMap;
    }

    private CompletableFuture<GameMapDescriptor> loadMap(GameMapDescriptor descriptor) {
        if (descriptor.backingType() != BackingType.WORLD) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map " + descriptor.id() + " must be world-backed for MapManager.setMap(...)."));
        }

        try {
            mapService.extractBackingAsset(descriptor);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        String worldName = worldName();
        module.log("Loading map " + descriptor.id() + " into world " + worldName + ".");
        return worldService.loadSlimeWorld(descriptor.assetPath(), worldName)
            .thenApply(world -> {
                GameWorld gameWorld = module.defaultWorld();
                gameWorld.setBukkitWorld(world);
                worldService.markWorldOwnedBy(module, world);
                currentMap = descriptor;
                module.log("Loaded map " + descriptor.id() + " into world " + world.getName() + ".");
                return descriptor;
            });
    }

    private String worldName() {
        return sanitize(module.id()) + "_" + module.index();
    }

    private String sanitize(String value) {
        String sanitized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return sanitized.isBlank() ? "game" : sanitized;
    }
}
