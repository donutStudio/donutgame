package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class MapManager {
    public static final String DEFAULT_MAP_ID = "donutgame_default";

    private final GameModule module;
    private final MapService mapService;
    private final WorldService worldService;
    private volatile GameMap currentMap;
    private volatile GameMap initialMap;
    private volatile GameWorld currentWorld;
    private volatile CompletableFuture<GameMap> pendingMapLoad;

    public MapManager(GameModule module, MapService mapService, WorldService worldService) {
        this.module = module;
        this.mapService = mapService;
        this.worldService = worldService;
    }

    public CompletableFuture<GameMap> setMap(String mapId) {
        Objects.requireNonNull(mapId, "mapId");
        return setMap(mapService.loadGameMap(mapService.getGameMapDescriptor(mapId)));
    }

    public CompletableFuture<GameMap> setMap(GameMap map) {
        Objects.requireNonNull(map, "map");
        CompletableFuture<GameMap> future = loadMap(map);
        pendingMapLoad = future;
        return future;
    }

    public CompletableFuture<GameMap> resetMap() {
        GameMap map = initialMap;
        if (map == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No initial map has been loaded."));
        }
        return setMap(map);
    }

    public CompletableFuture<GameMap> whenReady() {
        CompletableFuture<GameMap> future = pendingMapLoad;
        if (future != null) {
            return future;
        }
        GameMap map = currentMap;
        return map == null
            ? CompletableFuture.failedFuture(new IllegalStateException("Module did not load a map before onLoad. Configure a maps list or call mapManager().setMap(...) in beforeLoad()."))
            : CompletableFuture.completedFuture(map);
    }

    private CompletableFuture<GameMap> loadMap(GameMap map) {
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        GameMapDescriptor template = descriptor.backingType() == BackingType.WORLD ? descriptor : mapService.getGameMapDescriptor(DEFAULT_MAP_ID);
        if (template.backingType() != BackingType.WORLD) return CompletableFuture.failedFuture(new IllegalStateException("Default map " + DEFAULT_MAP_ID + " must be world-backed."));
        GameWorld existingWorld = currentWorld;
        String worldId = module.id() + "_" + module.index();
        String stagedName = existingWorld == null ? worldId : worldId + "__staged_" + System.nanoTime();
        module.log("Loading map " + map.id() + " for active game " + module.index() + ".");
        module.setTransitioning(true);
        return worldService.loadSlimeWorld(template.assetPath(), stagedName, worldId).thenCompose(bukkitWorld -> {
            GameWorld stagedWorld = new GameWorld(worldId, bukkitWorld, worldService);
            return prepareWorld(stagedWorld, map, descriptor).thenCompose(unused -> activateWorld(map, stagedWorld, existingWorld));
        }).whenComplete((ignored, throwable) -> module.setTransitioning(false));
    }

    public CompletableFuture<Void> placeMap(String mapId, Location location) {
        return placeMap(mapId, location, MapRotation.DEG_0);
    }

    public CompletableFuture<Void> placeMap(String mapId, Location location, MapRotation rotation) {
        Objects.requireNonNull(mapId, "mapId");
        return placeMap(mapService.loadGameMap(mapService.getGameMapDescriptor(mapId)), location, rotation);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location) {
        return placeMap(map, location, MapRotation.DEG_0);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location, MapRotation rotation) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        if (descriptor.backingType() == BackingType.WORLD) return CompletableFuture.failedFuture(new IllegalArgumentException("World-backed maps cannot be placed into an existing world."));
        GameWorld world = currentWorld;
        if (world == null) return CompletableFuture.failedFuture(new IllegalStateException("This game does not currently have a world."));
        if (!world.bukkitWorld().equals(location.getWorld())) return CompletableFuture.failedFuture(new IllegalArgumentException("Location must be inside the current game world."));
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            registerMapData(world, map, location, rotation, null);
            return CompletableFuture.completedFuture(null);
        }
        return worldService.pasteSchematic(descriptor.assetPath(), world.bukkitWorld(), location, rotation).thenAccept(metadata -> registerMapData(world, map, location, rotation, metadata));
    }

    public CompletableFuture<Void> unloadWorld() {
        GameWorld world = currentWorld;
        if (world == null) {
            currentMap = null;
            return CompletableFuture.completedFuture(null);
        }
        return worldService.unloadWorld(world.bukkitWorld().getName()).thenRun(() -> {
            if (currentWorld == world) {
                currentWorld = null;
                currentMap = null;
            }
        });
    }

    public GameWorld world() {
        return currentWorld;
    }

    public GameMap map() {
        return currentMap;
    }

    private CompletableFuture<Void> prepareWorld(GameWorld world, GameMap map, GameMapDescriptor descriptor) {
        world.clearMapData();
        if (descriptor.backingType() != BackingType.SCHEMATIC) {
            registerMapData(world, map, null, MapRotation.DEG_0, null);
            return CompletableFuture.completedFuture(null);
        }
        Location origin = new Location(world.bukkitWorld(), 0, 0, 0);
        return worldService.pasteSchematic(descriptor.assetPath(), world.bukkitWorld(), origin, MapRotation.DEG_0).thenAccept(metadata -> registerMapData(world, map, origin, MapRotation.DEG_0, metadata));
    }

    private CompletableFuture<GameMap> activateWorld(GameMap map, GameWorld stagedWorld, GameWorld existingWorld) {
        String oldWorldName = existingWorld == null ? null : existingWorld.bukkitWorld().getName();
        GameWorld liveWorld = existingWorld == null ? stagedWorld : existingWorld;
        if (existingWorld != null) {
            existingWorld.replaceBukkitWorld(stagedWorld.bukkitWorld());
            existingWorld.clearMapData();
            stagedWorld.copyPoints().forEach((name, entries) -> entries.forEach(point -> existingWorld.addPoint(point, name)));
            stagedWorld.copyRegions().forEach((name, entries) -> entries.forEach(region -> existingWorld.addRegion(region, name)));
        }
        currentMap = map;
        if (initialMap == null) {
            initialMap = map;
        }
        currentWorld = liveWorld;
        module.log("Map " + map.id() + " loaded into world " + liveWorld.name() + " (" + liveWorld.bukkitWorld().getName() + ").");
        return teleportPlayers(liveWorld).thenRun(() -> {
            if (module.hasStarted()) {
                module.playerManager().activatePostStartPlayers();
            } else {
                module.playerManager().activatePendingPlayers();
            }
        }).thenCompose(unused -> oldWorldName == null || oldWorldName.equals(liveWorld.bukkitWorld().getName()) ? CompletableFuture.completedFuture(map) : worldService.unloadWorld(oldWorldName).thenApply(result -> map));
    }

    private CompletableFuture<Void> teleportPlayers(GameWorld world) {
        GameLocation spawn = world.getPoint("spawn");
        if (spawn == null) spawn = world.worldSpawn();
        GameLocation targetSpawn = spawn;
        CompletableFuture<Void> future = new CompletableFuture<>();
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            try {
                List<GamePlayer> movedPlayers = new ArrayList<>();
                List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
                World bukkitWorld = world.bukkitWorld();
                Location destination = targetSpawn.toBukkit(bukkitWorld);
                destination.getChunk().load();
                for (GamePlayer player : module.playerManager().getPlayers()) {
                    Player bukkitPlayer = player.player();
                    if (bukkitPlayer == null) continue;
                    player.setSpawnPoint(targetSpawn);
                    module.log("Teleporting registered player " + bukkitPlayer.getName() + " to map spawn " + targetSpawn.x() + ", " + targetSpawn.y() + ", " + targetSpawn.z() + ".");
                    bukkitPlayer.closeInventory();
                    movedPlayers.add(player);
                    resetTransientPlayerState(bukkitPlayer);
                    teleports.add(bukkitPlayer.teleportAsync(destination.clone()).exceptionally(throwable -> false));
                }
                CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) ->
                    module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                            return;
                        }
                        for (int i = 0; i < movedPlayers.size(); i++) {
                            GamePlayer movedGamePlayer = movedPlayers.get(i);
                            Player movedPlayer = movedGamePlayer.player();
                            if (movedPlayer == null) continue;
                            if (!Boolean.TRUE.equals(teleports.get(i).getNow(false))) {
                                future.completeExceptionally(new IllegalStateException("Failed to teleport player " + movedPlayer.getName() + " during map handoff."));
                                return;
                            }
                            movedGamePlayer.syncStateAfterTrackingReset();
                            movedPlayer.updateInventory();
                        }
                        module.uiManager().refreshPlayerStateAfterTrackingReset();
                        future.complete(null);
                    }, 2L)
                );
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void resetTransientPlayerState(Player player) {
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setNoDamageTicks(40);
        player.setRemainingAir(player.getMaximumAir());
        player.setExhaustion(0);
    }

    private void registerMapData(GameWorld world, GameMap map, Location origin, MapRotation rotation, WorldService.SchematicMetadata schematicMetadata) {
        GameMapDescriptor descriptor = mapService.getGameMapDescriptor(map.id());
        if (descriptor.backingType() == BackingType.WORLD) {
            map.points().forEach((name, points) -> points.forEach(point -> world.addPoint(new GameLocation(point.x(), point.y(), point.z()), name)));
            map.regions().forEach((name, regions) -> regions.forEach(region -> world.addRegion(new GameRegion(new GameLocation(region.min().x(), region.min().y(), region.min().z()), new GameLocation(region.max().x(), region.max().y(), region.max().z())), name)));
            return;
        }
        Location base = origin == null ? new Location(world.bukkitWorld(), 0, 0, 0) : origin;
        WorldService.SchematicMetadata metadata = descriptor.backingType() == BackingType.SCHEMATIC ? (schematicMetadata == null ? worldService.getSchematicMetadata(descriptor.assetPath()) : schematicMetadata) : null;
        map.points().forEach((name, points) -> points.forEach(point -> world.addPoint(GameLocation.fromBukkit(transformPoint(base, point, rotation, metadata)), name)));
        map.regions().forEach((name, regions) -> regions.forEach(region -> world.addRegion(GameRegion.fromBoundingBox(transformRegion(base, region, rotation, metadata)), name)));
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

    private BoundingBox transformRegion(Location origin, GameMap.MapRegion region, MapRotation rotation, WorldService.SchematicMetadata metadata) {
        int[] xs = {region.min().x(), region.max().x()};
        int[] ys = {region.min().y(), region.max().y()};
        int[] zs = {region.min().z(), region.max().z()};
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int x : xs) for (int z : zs) {
            Location transformed = transformPoint(origin, new GameMap.MapPoint(x, 0, z), rotation, metadata);
            minX = Math.min(minX, transformed.getBlockX());
            maxX = Math.max(maxX, transformed.getBlockX());
            minZ = Math.min(minZ, transformed.getBlockZ());
            maxZ = Math.max(maxZ, transformed.getBlockZ());
        }
        return new BoundingBox(minX, origin.getBlockY() + Math.min(ys[0], ys[1]), minZ, maxX, origin.getBlockY() + Math.max(ys[0], ys[1]), maxZ);
    }
}
