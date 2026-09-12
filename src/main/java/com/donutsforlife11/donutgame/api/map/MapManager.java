package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;

import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class MapManager {
    private final GameModule module;
    private final MapService mapService;
    private final WorldService worldService;
    private volatile GameMap currentMap;
    private volatile CompletableFuture<GameMap> pendingMapLoad;

    public MapManager(GameModule module, MapService mapService, WorldService worldService) {
        this.module = module;
        this.mapService = mapService;
        this.worldService = worldService;
    }

    public GameMap mapFromId(String id) {
        Objects.requireNonNull(id, "id");
        return mapService.load(id);
    }

    public CompletableFuture<GameMap> load(String id) {
        Objects.requireNonNull(id, "id");
        return load(mapFromId(id));
    }

    public CompletableFuture<GameMap> load(GameMap map) {
        Objects.requireNonNull(map, "map");
        CompletableFuture<GameMap> future = loadMap(map);
        pendingMapLoad = future;
        return future;
    }

    public CompletableFuture<GameMap> setMap(String mapId) {
        return load(mapId);
    }

    public CompletableFuture<GameMap> setMap(GameMap map) {
        return load(map);
    }

    public CompletableFuture<GameMap> place(String id, GameLocation location) {
        return place(id, location, MapRotation.DEG_0);
    }

    public CompletableFuture<GameMap> place(String id, GameLocation location, MapRotation rotation) {
        Objects.requireNonNull(id, "id");
        return place(mapFromId(id), location, rotation);
    }

    public CompletableFuture<GameMap> place(GameMap map, GameLocation location) {
        return place(map, location, MapRotation.DEG_0);
    }

    public CompletableFuture<GameMap> place(GameMap map, GameLocation location, MapRotation rotation) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        if (map.backingType() == BackingType.WORLD) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("World-backed maps cannot be placed into an existing world."));
        }
        GameWorld world = world();
        if (world.bukkitWorld() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Module does not have a loaded world."));
        }

        GameLocation origin = location.inWorld(world);
        if (map.backingType() == BackingType.NONE) {
            GameMap placement = map.placed(world, origin, rotation, null);
            registerMapData(world, placement);
            return CompletableFuture.completedFuture(placement);
        }

        if (map.assetPath() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map " + map.id() + " does not have an extracted schematic asset."));
        }

        Location bukkitLocation = origin.toBukkit(world.bukkitWorld());
        return worldService.pasteSchematic(map.assetPath(), world.bukkitWorld(), bukkitLocation, rotation)
            .thenApply(metadata -> {
                GameMap placement = map.placed(world, origin, rotation, metadata);
                registerMapData(world, placement);
                return placement;
            });
    }

    public CompletableFuture<GameMap> whenReady() {
        CompletableFuture<GameMap> future = pendingMapLoad;
        if (future != null) {
            return future;
        }
        GameMap map = currentMap;
        return map == null
            ? CompletableFuture.failedFuture(new IllegalStateException("Module did not set a map in beforeLoad(). Configure 'map', configure 'maps', or call mapManager().setMap(...)."))
            : CompletableFuture.completedFuture(map);
    }

    public CompletableFuture<Void> unloadWorlds() {
        return worldService.unloadModuleWorlds(module).thenRun(() -> {
            currentMap = null;
            world().clearMapData();
        });
    }

    public GameMap map() {
        return currentMap;
    }

    public GameWorld world() {
        return module.defaultWorld();
    }

    private CompletableFuture<GameMap> loadMap(GameMap map) {
        if (map.backingType() != BackingType.WORLD) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map " + map.id() + " must be world-backed for MapManager.load(...)."));
        }
        if (map.assetPath() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map " + map.id() + " does not have an extracted world asset."));
        }

        String worldName = worldName();
        module.log("Loading map " + map.id() + " into world " + worldName + ".");
        CompletableFuture<Void> unloadExisting = world().bukkitWorld() == null
            ? CompletableFuture.completedFuture(null)
            : worldService.unloadModuleWorlds(module);
        return unloadExisting.thenCompose(ignored -> worldService.loadSlimeWorld(map.assetPath(), worldName))
            .thenApply(world -> {
                GameWorld gameWorld = module.defaultWorld();
                gameWorld.setBukkitWorld(world);
                worldService.markWorldOwnedBy(module, world);
                GameMap placed = map.placed(gameWorld, new GameLocation(gameWorld, 0, 0, 0), MapRotation.DEG_0, null);
                gameWorld.clearMapData();
                registerMapData(gameWorld, placed);
                currentMap = placed;
                module.log("Loaded map " + map.id() + " into world " + world.getName() + ".");
                return placed;
            });
    }

    private void registerMapData(GameWorld world, GameMap map) {
        map.points().forEach((name, points) -> points.forEach(point ->
            world.addPoint(name, new GameLocation(world, point.x(), point.y(), point.z()))
        ));
        map.regions().forEach((name, regions) -> regions.forEach(region ->
            world.addRegion(name, new GameRegion(
                new GameLocation(world, region.min().x(), region.min().y(), region.min().z()),
                new GameLocation(world, region.max().x(), region.max().y(), region.max().z())
            ))
        ));
    }

    private String worldName() {
        return sanitize(module.id()) + "_" + module.index();
    }

    private String sanitize(String value) {
        String sanitized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return sanitized.isBlank() ? "game" : sanitized;
    }
}
