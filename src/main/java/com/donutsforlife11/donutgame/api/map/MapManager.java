package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;

import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class MapManager {
    public static final String DEFAULT_MAP_ID = "donutgame_default";

    private final GameModule module;
    private final MapService mapService;
    private final WorldService worldService;

    private volatile GameMap currentMap;
    private volatile GameWorld currentWorld;

    public MapManager(GameModule module, MapService mapService, WorldService worldService) {
        this.module = module;
        this.mapService = mapService;
        this.worldService = worldService;
    }

    public CompletableFuture<GameMap> setMap(String mapId) {
        Objects.requireNonNull(mapId, "mapId");

        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(mapId);
        if (descriptor == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown map: " + mapId));
        }

        return setMap(mapService.loadGameMap(descriptor));
    }

    public CompletableFuture<GameMap> setMap(GameMap map) {
        Objects.requireNonNull(map, "Map is null!");
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        GameMap gameMap = mapService.loadGameMap(descriptor);
        module.log("Loading map " + map.id() + " for active game " + module.index() + ".");
        GameMapDescriptor templateDescriptor = descriptor.backingType() == BackingType.WORLD
            ? descriptor
            : mapService.getGameMapDescriptor(DEFAULT_MAP_ID);
        if (templateDescriptor.backingType() != BackingType.WORLD) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Default map " + DEFAULT_MAP_ID + " must be world-backed.")
            );
        }
        GameWorld existingWorld = currentWorld;
        String logicalWorldName = module.id() + "_" + module.index();
        String stagedWorldName = existingWorld == null
            ? logicalWorldName
            : logicalWorldName + "__staged_" + System.nanoTime();
        return worldService.loadSlimeWorld(templateDescriptor.assetPath(), stagedWorldName, logicalWorldName)
            .thenCompose(stagedBukkitWorld -> {
                GameWorld stagedWorld = new GameWorld(logicalWorldName, stagedBukkitWorld, worldService);
                return prepareWorld(stagedWorld, gameMap, descriptor)
                    .thenCompose(ignored -> activateWorld(gameMap, stagedWorld, existingWorld));
            });
    }

    public CompletableFuture<Void> placeMap(String mapId, Location location) {
        return placeMap(mapId, location, MapRotation.DEG_0);
    }

    public CompletableFuture<Void> placeMap(String mapId, Location location, MapRotation rotation) {
        Objects.requireNonNull(mapId, "mapId");

        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(mapId);
        if (descriptor == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown map: " + mapId));
        }

        return placeMap(mapService.loadGameMap(descriptor), location, rotation);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location) {
        return placeMap(map, location, MapRotation.DEG_0);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location, MapRotation rotation) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        if (descriptor.backingType() == BackingType.WORLD) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("World-backed maps cannot be placed into an existing world."));
        }
        GameWorld world = currentWorld;
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("This game does not currently have a world."));
        }
        if (!world.bukkitWorld().equals(location.getWorld())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Location must be inside the current game world."));
        }
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            registerMapData(world, map, location, rotation, null);
        }
        return worldService.pasteSchematic(descriptor.assetPath(), world.bukkitWorld(), location, rotation)
            .thenAccept(metadata -> registerMapData(world, map, location, rotation, metadata));
    }

    public CompletableFuture<Void> unloadCurrentWorld() {
        GameWorld world = currentWorld;
        if (world == null) {
            currentMap = null;
            return CompletableFuture.completedFuture(null);
        }
        String playerStateId = worldService.getPlayerStateId(world.bukkitWorld());
        return worldService.unloadWorld(world.bukkitWorld().getName()).thenRun(() -> {
            worldService.clearPlayerState(playerStateId);
            if (currentWorld == world) {
                currentWorld = null;
                currentMap = null;
            }
        });
    }

    public GameWorld currentWorld() {
        return currentWorld;
    }

    public GameMap currentMap() {
        return currentMap;
    }

    private CompletableFuture<Void> prepareWorld(GameWorld world, GameMap map, GameMapDescriptor descriptor) {
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            world.clearMapData();
            registerMapData(world, map, null, MapRotation.DEG_0, null);
            return CompletableFuture.completedFuture(null);
        }
        Location origin = new Location(world.bukkitWorld(), 0, 0, 0);
        return worldService.pasteSchematic(descriptor.assetPath(), world.bukkitWorld(), origin, MapRotation.DEG_0)
            .thenAccept(metadata -> {
                world.clearMapData();
                registerMapData(world, map, origin, MapRotation.DEG_0, metadata);
            });
    }

    private CompletableFuture<GameMap> activateWorld(
        GameMap gameMap,
        GameWorld stagedWorld,
        GameWorld existingWorld
    ) {
        String oldWorldName = existingWorld == null
            ? null
            : existingWorld.bukkitWorld().getName();
        GameWorld liveWorld = existingWorld == null
            ? stagedWorld
            : existingWorld;
        if (existingWorld != null) {
            existingWorld.replaceBukkitWorld(stagedWorld.bukkitWorld());
            existingWorld.clearMapData();
            copyMapData(stagedWorld, existingWorld);
        }
        currentMap = gameMap;
        currentWorld = liveWorld;
        module.log(
            "Map " + gameMap.id()
                + " loaded into world " + liveWorld.name()
                + " (" + liveWorld.bukkitWorld().getName() + ")."
        );
        /*
        * IMPORTANT:
        *
        * Move players who are ALREADY registered first.
        *
        * Pending players are registered afterwards. registerNow() already
        * teleports a newly registered player to the game spawn, so activating
        * them before teleportPlayers() caused them to be teleported twice
        * during initial game loading.
        */
        return teleportPlayers(liveWorld)
            .thenRun(module.playerManager()::activatePendingPlayers)
            .thenCompose(ignored -> {
                if (
                    oldWorldName == null
                        || oldWorldName.equals(liveWorld.bukkitWorld().getName())
                ) {
                    return CompletableFuture.completedFuture(gameMap);
                }

                return worldService.unloadWorld(oldWorldName)
                    .thenApply(unused -> gameMap);
            });
    }

    private CompletableFuture<Void> teleportPlayers(GameWorld world) {
        List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (var player : module.playerManager().getPlayers()) {
            if (player.player() == null) {
                continue;
            }
            GameLocation target = world.getPoints("spawn").isEmpty()
                ? world.spawnLocation()
                : world.getPoints("spawn").get(0);
            player.setRespawnLocation(target);
            module.log("Teleporting registered player " + player.player().getName() + " to map spawn " + target.x() + ", " + target.y() + ", " + target.z() + ".");
            teleports.add(player.player().teleportAsync(target.toBukkit(world.bukkitWorld())));
        }
        return CompletableFuture.allOf(teleports.toArray(new CompletableFuture[0]));
    }

    private void copyMapData(GameWorld source, GameWorld target) {
        for (Map.Entry<String, List<GameLocation>> entry : source.copyPoints().entrySet()) {
            for (GameLocation point : entry.getValue()) {
                target.addPoint(point, entry.getKey());
            }
        }
        for (Map.Entry<String, List<GameRegion>> entry : source.copyRegions().entrySet()) {
            for (GameRegion region : entry.getValue()) {
                target.addRegion(region, entry.getKey());
            }
        }
    }

    private void registerMapData(
        GameWorld world,
        GameMap map,
        Location origin,
        MapRotation rotation,
        WorldService.SchematicMetadata schematicMetadata
    ) {
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        if (descriptor.backingType() == BackingType.WORLD) {
            for (Map.Entry<String, List<GameMap.MapPoint>> entry : map.points().entrySet()) {
                for (GameMap.MapPoint point : entry.getValue()) {
                    world.addPoint(new GameLocation(point.x(), point.y(), point.z()), entry.getKey());
                }
            }
            for (Map.Entry<String, List<GameMap.MapRegion>> entry : map.regions().entrySet()) {
                for (GameMap.MapRegion region : entry.getValue()) {
                    world.addRegion(toGameRegion(region), entry.getKey());
                }
            }
            return;
        }
        Location baseLocation = origin == null ? new Location(world.bukkitWorld(), 0, 0, 0) : origin;
        WorldService.SchematicMetadata metadata = descriptor.backingType() == BackingType.SCHEMATIC
            ? (schematicMetadata == null ? worldService.getSchematicMetadata(descriptor.assetPath()) : schematicMetadata)
            : null;
        for (Map.Entry<String, List<GameMap.MapPoint>> entry : map.points().entrySet()) {
            for (GameMap.MapPoint point : entry.getValue()) {
                world.addPoint(GameLocation.fromBukkit(transformPoint(baseLocation, point, rotation, metadata)), entry.getKey());
            }
        }
        for (Map.Entry<String, List<GameMap.MapRegion>> entry : map.regions().entrySet()) {
            for (GameMap.MapRegion region : entry.getValue()) {
                world.addRegion(GameRegion.fromBoundingBox(transformRegion(baseLocation, region, rotation, metadata)), entry.getKey());
            }
        }
    }

    private GameRegion toGameRegion(GameMap.MapRegion region) {
        return new GameRegion(
            new GameLocation(region.min().x(), region.min().y(), region.min().z()),
            new GameLocation(region.max().x(), region.max().y(), region.max().z())
        );
    }

    private Location transformPoint(Location origin, GameMap.MapPoint point, MapRotation rotation, WorldService.SchematicMetadata metadata) {
        int width = metadata == null ? 0 : metadata.width();
        int depth = metadata == null ? 0 : metadata.depth();
        int x = point.x();
        int y = point.y();
        int z = point.z();

        return switch (rotation) {
            case DEG_0 -> new Location(origin.getWorld(), origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
            case DEG_90 -> new Location(origin.getWorld(), origin.getBlockX() + z, origin.getBlockY() + y, origin.getBlockZ() - x + Math.max(0, width - 1));
            case DEG_180 -> new Location(origin.getWorld(), origin.getBlockX() - x + Math.max(0, width - 1), origin.getBlockY() + y, origin.getBlockZ() - z + Math.max(0, depth - 1));
            case DEG_270 -> new Location(origin.getWorld(), origin.getBlockX() - z + Math.max(0, depth - 1), origin.getBlockY() + y, origin.getBlockZ() + x);
        };
    }

    private org.bukkit.util.BoundingBox transformRegion(Location origin, GameMap.MapRegion region, MapRotation rotation, WorldService.SchematicMetadata metadata) {
        int[] xs = {region.min().x(), region.max().x()};
        int[] ys = {region.min().y(), region.max().y()};
        int[] zs = {region.min().z(), region.max().z()};
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int x : xs) {
            for (int z : zs) {
                Location transformed = transformPoint(origin, new GameMap.MapPoint(x, 0, z), rotation, metadata);
                minX = Math.min(minX, transformed.getBlockX());
                maxX = Math.max(maxX, transformed.getBlockX());
                minZ = Math.min(minZ, transformed.getBlockZ());
                maxZ = Math.max(maxZ, transformed.getBlockZ());
            }
        }
        double minY = origin.getBlockY() + Math.min(ys[0], ys[1]);
        double maxY = origin.getBlockY() + Math.max(ys[0], ys[1]);
        return new org.bukkit.util.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
