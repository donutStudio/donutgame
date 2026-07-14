package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

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
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown map: " + mapId)
            );
        }

        GameMap map = mapService.loadGameMap(descriptor);
        return setMap(map);
    }
    public CompletableFuture<GameMap> setMap(GameMap map) {
        Objects.requireNonNull(map, "Map is null!");
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        GameMap gameMap = mapService.loadGameMap(descriptor);
        GameMapDescriptor templateDescriptor = descriptor.backingType() == BackingType.WORLD
            ? descriptor
            : mapService.getGameMapDescriptor(DEFAULT_MAP_ID);
        if (templateDescriptor.backingType() != BackingType.WORLD) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Default map " + DEFAULT_MAP_ID + " must be world-backed.")
            );
        }
        String instanceWorldName = module.id() + "_" + module.index() + "_" + gameMap.id();
        CompletableFuture<GameMap> future = worldService.loadSlimeWorld(templateDescriptor.assetPath(), instanceWorldName)
            .thenCompose(world -> {
                GameWorld newWorld = new GameWorld(world);
                CompletableFuture<Void> registerMapFuture = CompletableFuture.completedFuture(null);
                if (descriptor.backingType() == BackingType.SCHEMATIC) {
                    Location schemLoc = new Location(newWorld.getBukkitWorld(), 0, 0, 0);
                    registerMapFuture = worldService.pasteSchematic(descriptor.assetPath(), newWorld.getBukkitWorld(), schemLoc, MapRotation.DEG_0)
                        .thenAccept(metadata -> {
                            registerMapData(newWorld, gameMap, schemLoc, MapRotation.DEG_0, metadata);
                        });
                } else {
                    registerMapData(newWorld, gameMap, null, MapRotation.DEG_0, null);
                }
                return registerMapFuture.thenApply(ignored -> {
                    currentMap = gameMap;
                    currentWorld = newWorld;
                    return gameMap;
                });
            })
            .thenCompose(loadedMap -> {
                List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
                for (Player player : module.playerManager().getPlayers()) {
                    teleports.add(player.teleportAsync(currentWorld.getBukkitWorld().getSpawnLocation()));
                }
                return CompletableFuture.allOf(teleports.toArray(new CompletableFuture[0]))
                    .thenApply(ignored -> loadedMap);
            });
        return future;
    }


    public CompletableFuture<Void> placeMap(String mapId, Location location) {
        return placeMap(mapId, location, MapRotation.DEG_0);
    }
    public CompletableFuture<Void> placeMap(String mapId, Location location, MapRotation rotation) {
        Objects.requireNonNull(mapId, "mapId");

        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(mapId);
        if (descriptor == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown map: " + mapId)
            );
        }

        GameMap map = mapService.loadGameMap(descriptor);
        return placeMap(map, location, rotation);
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
        if (!world.getBukkitWorld().equals(location.getWorld())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Location must be inside the current game world."));
        }
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            registerMapData(world, map, location, rotation, null);
        }
        return worldService.pasteSchematic(descriptor.assetPath(), world.getBukkitWorld(), location, rotation)
            .thenAccept(metadata -> {
                registerMapData(world, map, location, rotation, metadata);
            });
    }

    public CompletableFuture<Void> unloadCurrentWorld() {
        return worldService.unloadWorld(currentWorld.getBukkitWorld().getName());
    }

    public GameWorld currentWorld() {
        return currentWorld;
    }
    public GameMap currentMap() {
        return currentMap;
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
                    world.addPoint(new Location(world.getBukkitWorld(), point.x(), point.y(), point.z()), entry.getKey());
                }
            }
            for (Map.Entry<String, List<GameMap.MapRegion>> entry : map.regions().entrySet()) {
                for (GameMap.MapRegion region : entry.getValue()) {
                    world.addRegion(toBoundingBox(region), entry.getKey());
                }
            }
            return;
        }
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            Location baseLocation = origin == null ? new Location(world.getBukkitWorld(), 0, 0, 0) : origin;
            for (Map.Entry<String, List<GameMap.MapPoint>> entry : map.points().entrySet()) {
                for (GameMap.MapPoint point : entry.getValue()) {
                    world.addPoint(transformPoint(baseLocation, point, rotation, null), entry.getKey());
                }
            }
            for (Map.Entry<String, List<GameMap.MapRegion>> entry : map.regions().entrySet()) {
                for (GameMap.MapRegion region : entry.getValue()) {
                    world.addRegion(transformRegion(baseLocation, region, rotation, null), entry.getKey());
                }
            }
            return;
        }
        Location baseLocation = origin == null ? new Location(world.getBukkitWorld(), 0, 0, 0) : origin;
        WorldService.SchematicMetadata metadata = schematicMetadata == null ? worldService.getSchematicMetadata(descriptor.assetPath()) : schematicMetadata;
        for (Map.Entry<String, List<GameMap.MapPoint>> entry : map.points().entrySet()) {
            for (GameMap.MapPoint point : entry.getValue()) {
                world.addPoint(transformPoint(baseLocation, point, rotation, metadata), entry.getKey());
            }
        }
        for (Map.Entry<String, List<GameMap.MapRegion>> entry : map.regions().entrySet()) {
            for (GameMap.MapRegion region : entry.getValue()) {
                world.addRegion(transformRegion(baseLocation, region, rotation, metadata), entry.getKey());
            }
        }
    }
    private BoundingBox toBoundingBox(GameMap.MapRegion region) {
        return new BoundingBox(
            Math.min(region.min().x(), region.max().x()),
            Math.min(region.min().y(), region.max().y()),
            Math.min(region.min().z(), region.max().z()),
            Math.max(region.min().x(), region.max().x()),
            Math.max(region.min().y(), region.max().y()),
            Math.max(region.min().z(), region.max().z())
        );
    }
    private Location transformPoint(Location origin, GameMap.MapPoint point, MapRotation rotation, WorldService.SchematicMetadata metadata) {
        int width = metadata == null ? 0 : metadata.width();
        int depth = metadata == null ? 0 : metadata.depth();
        int x = point.x();
        int y = point.y();
        int z = point.z();

        return switch(rotation) {
            case DEG_0 -> new Location(origin.getWorld(), origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
            case DEG_90 -> new Location(origin.getWorld(), origin.getBlockX() + z, origin.getBlockY() + y, origin.getBlockZ() - x + Math.max(0, width - 1));
            case DEG_180 -> new Location(origin.getWorld(), origin.getBlockX() - x + Math.max(0, width - 1), origin.getBlockY() + y, origin.getBlockZ() - z + Math.max(0, depth - 1));
            case DEG_270 -> new Location(origin.getWorld(), origin.getBlockX() - z + Math.max(0, depth - 1), origin.getBlockY() + y, origin.getBlockZ() + x);
        };
    }
    private BoundingBox transformRegion(Location origin, GameMap.MapRegion region, MapRotation rotation, WorldService.SchematicMetadata metadata) {
        int[] xs = { region.min().x(), region.max().x() };
        int[] ys = { region.min().y(), region.max().y() };
        int[] zs = { region.min().z(), region.max().z() };
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
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}