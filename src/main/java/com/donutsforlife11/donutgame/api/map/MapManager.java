package com.donutsforlife11.donutgame.api.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.util.WorldManager;

import io.papermc.paper.event.entity.EntityMoveEvent;

public final class MapManager implements Listener {
    public static final String DEFAULT_MAP_PATH = "donutgame_default.dmap";

    private final Donutgame plugin;
    private final int gameIndex;
    private final String gameId;
    private final PlayerManager playerManager;
    private final MapRepository mapRepository;
    private final WorldManager worldManager;
    private final List<BiConsumer<GameMap, GameWorld>> mapChangeActions = new CopyOnWriteArrayList<>();
    private final java.util.Set<CompletableFuture<?>> inFlightOperations = ConcurrentHashMap.newKeySet();

    private volatile GameMap currentMap;
    private volatile GameWorld currentWorld;

    public MapManager(
        Donutgame plugin,
        int gameIndex,
        String gameId,
        PlayerManager playerManager,
        MapRepository mapRepository,
        WorldManager worldManager
    ) {
        this.plugin = plugin;
        this.gameIndex = gameIndex;
        this.gameId = gameId;
        this.playerManager = playerManager;
        this.mapRepository = mapRepository;
        this.worldManager = worldManager;

        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public CompletableFuture<GameMap> setMap(GameMap map) {
        Objects.requireNonNull(map, "map");

        GameMap resolvedMap = resolveMap(map);
        String worldName = currentWorld == null
            ? gameId + "_" + gameIndex
            : currentWorld.getBukkitWorld().getName();

        GameMap templateSourceMap = resolvedMap.isSlimeBacked() ? resolvedMap : mapRepository.load(DEFAULT_MAP_PATH);
        if (!resolvedMap.isSlimeBacked()) {
            if (!templateSourceMap.isSlimeBacked()) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("Default map " + DEFAULT_MAP_PATH + " must be slime-backed.")
                );
            }
        }

        List<Player> players = new ArrayList<>(playerManager.getPlayers());
        CompletableFuture<GameMap> future = evacuatePlayers(players)
            .thenCompose(ignored -> unloadCurrentWorld())
            .thenCompose(ignored -> worldManager.loadSlimeWorld(templateSourceMap.slimePath(), templateSourceMap.getId(), worldName))
            .thenCompose(world -> {
                GameWorld gameWorld = new GameWorld(world, worldManager::clearArea);
                CompletableFuture<Void> loadMapFuture = CompletableFuture.completedFuture(null);

                if (resolvedMap.isSchematicBacked()) {
                    loadMapFuture = worldManager.pasteSchematic(
                        resolvedMap.schematicPath(),
                        gameWorld.getBukkitWorld(),
                        new Location(gameWorld.getBukkitWorld(), 0, 0, 0),
                        MapRotation.DEG_0
                    ).thenAccept(metadata -> registerMapData(
                        gameWorld,
                        resolvedMap,
                        new Location(gameWorld.getBukkitWorld(), 0, 0, 0),
                        MapRotation.DEG_0,
                        metadata
                    ));
                } else {
                    registerMapData(gameWorld, resolvedMap, null, MapRotation.DEG_0, null);
                }

                return loadMapFuture.thenApply(ignoredValue -> {
                    currentMap = resolvedMap;
                    currentWorld = gameWorld;
                    notifyMapChanged(resolvedMap, gameWorld);
                    return resolvedMap;
                });
            })
            .thenCompose(loadedMap -> teleportPlayersToCurrentWorld(players).thenApply(ignored -> loadedMap));

        return track(future);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location) {
        return placeMap(map, location, MapRotation.DEG_0);
    }

    public CompletableFuture<Void> placeMap(GameMap map, Location location, MapRotation rotation) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");

        if (map.isSlimeBacked()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Slime-backed maps cannot be placed into an existing world."));
        }

        GameWorld world = currentWorld;
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("This game does not currently have a world."));
        }

        if (!world.getBukkitWorld().equals(location.getWorld())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Location must be inside the current game world."));
        }

        if (!map.isSchematicBacked()) {
            registerMapData(world, map, location, rotation, null);
            return CompletableFuture.completedFuture(null);
        }

        return worldManager.pasteSchematic(map.schematicPath(), world.getBukkitWorld(), location, rotation)
            .thenAccept(metadata -> registerMapData(world, map, location, rotation, metadata));
    }

    public GameWorld currentWorld() {
        return currentWorld;
    }

    public GameMap currentMap() {
        return currentMap;
    }

    public void onMapChanged(BiConsumer<GameMap, GameWorld> action) {
        mapChangeActions.add(action);
    }

    public CompletableFuture<Void> awaitIdle() {
        CompletableFuture<?>[] pending = inFlightOperations.toArray(CompletableFuture[]::new);
        return pending.length == 0 ? CompletableFuture.completedFuture(null) : CompletableFuture.allOf(pending);
    }

    public CompletableFuture<GameMap> ensureWorld() {
        return currentWorld == null ? setMap(GameMap.fromPath(DEFAULT_MAP_PATH)) : CompletableFuture.completedFuture(currentMap);
    }

    public CompletableFuture<Boolean> teleportPlayerToSpawn(Player player) {
        if (currentWorld == null) {
            return CompletableFuture.completedFuture(false);
        }

        World targetWorld = currentWorld.getBukkitWorld();
        boolean alreadyInWorld = targetWorld.equals(player.getWorld());
        CompletableFuture<Boolean> future = player.teleportAsync(targetWorld.getSpawnLocation());
        future.thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && alreadyInWorld) {
                playerManager.notifyPlayerEnteredWorld(player);
            }
        });
        return future;
    }

    public CompletableFuture<Void> shutdown() {
        HandlerList.unregisterAll(this);
        return unloadCurrentWorld();
    }

    @EventHandler
    public void playerMove(PlayerMoveEvent event) {
        GameWorld world = currentWorld;
        if (world == null || event.getTo() == null || !event.hasChangedBlock()) {
            return;
        }

        world.handleRegionCheck(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void playerTeleport(PlayerTeleportEvent event) {
        GameWorld world = currentWorld;
        if (world == null || event.getTo() == null) {
            return;
        }

        world.handleRegionCheck(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void entityMove(EntityMoveEvent event) {
        GameWorld world = currentWorld;
        if (world == null || event.getTo() == null || !event.hasChangedBlock()) {
            return;
        }

        world.handleRegionCheck(event.getEntity(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void entityTeleport(EntityTeleportEvent event) {
        GameWorld world = currentWorld;
        if (world == null || event.getTo() == null) {
            return;
        }

        world.handleRegionCheck(event.getEntity(), event.getFrom(), event.getTo());
    }

    private CompletableFuture<Void> evacuatePlayers(List<Player> players) {
        if (currentWorld == null || players.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Location fallbackLocation = Bukkit.getWorlds().getFirst().getSpawnLocation();
        List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (Player player : players) {
            teleports.add(player.teleportAsync(fallbackLocation));
        }

        return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> teleportPlayersToCurrentWorld(List<Player> players) {
        if (currentWorld == null || players.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (Player player : players) {
            teleports.add(teleportPlayerToSpawn(player));
        }

        return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> unloadCurrentWorld() {
        GameWorld world = currentWorld;
        currentMap = null;
        currentWorld = null;
        notifyMapChanged(null, null);

        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        return worldManager.unloadWorld(world.getBukkitWorld().getName());
    }

    private GameMap resolveMap(GameMap map) {
        return map.isReference() ? mapRepository.load(map.mapPath()) : map;
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        inFlightOperations.add(future);
        future.whenComplete((ignored, error) -> inFlightOperations.remove(future));
        return future;
    }

    private void notifyMapChanged(GameMap map, GameWorld world) {
        for (BiConsumer<GameMap, GameWorld> action : mapChangeActions) {
            action.accept(map, world);
        }
    }

    private void registerMapData(
        GameWorld world,
        GameMap map,
        Location origin,
        MapRotation rotation,
        WorldManager.SchematicMetadata schematicMetadata
    ) {
        if (map.isSlimeBacked()) {
            for (Map.Entry<String, List<GameMap.IntPoint>> entry : map.points().entrySet()) {
                for (GameMap.IntPoint point : entry.getValue()) {
                    world.addPoint(new Location(world.getBukkitWorld(), point.x(), point.y(), point.z()), entry.getKey());
                }
            }

            for (Map.Entry<String, List<GameMap.IntRegion>> entry : map.regions().entrySet()) {
                for (GameMap.IntRegion region : entry.getValue()) {
                    world.addRegion(toBoundingBox(region), entry.getKey());
                }
            }

            return;
        }

        if (!map.isSchematicBacked()) {
            Location baseLocation = origin == null ? new Location(world.getBukkitWorld(), 0, 0, 0) : origin;

            for (Map.Entry<String, List<GameMap.IntPoint>> entry : map.points().entrySet()) {
                for (GameMap.IntPoint point : entry.getValue()) {
                    world.addPoint(transformPoint(baseLocation, point, rotation, null), entry.getKey());
                }
            }

            for (Map.Entry<String, List<GameMap.IntRegion>> entry : map.regions().entrySet()) {
                for (GameMap.IntRegion region : entry.getValue()) {
                    world.addRegion(transformRegion(baseLocation, region, rotation, null), entry.getKey());
                }
            }

            return;
        }

        Location baseLocation = origin == null ? new Location(world.getBukkitWorld(), 0, 0, 0) : origin;
        WorldManager.SchematicMetadata metadata = schematicMetadata == null
            ? worldManager.getSchematicMetadata(map.schematicPath())
            : schematicMetadata;

        for (Map.Entry<String, List<GameMap.IntPoint>> entry : map.points().entrySet()) {
            for (GameMap.IntPoint point : entry.getValue()) {
                world.addPoint(transformPoint(baseLocation, point, rotation, metadata), entry.getKey());
            }
        }

        for (Map.Entry<String, List<GameMap.IntRegion>> entry : map.regions().entrySet()) {
            for (GameMap.IntRegion region : entry.getValue()) {
                world.addRegion(transformRegion(baseLocation, region, rotation, metadata), entry.getKey());
            }
        }
    }

    private BoundingBox toBoundingBox(GameMap.IntRegion region) {
        return new BoundingBox(
            Math.min(region.min().x(), region.max().x()),
            Math.min(region.min().y(), region.max().y()),
            Math.min(region.min().z(), region.max().z()),
            Math.max(region.min().x(), region.max().x()),
            Math.max(region.min().y(), region.max().y()),
            Math.max(region.min().z(), region.max().z())
        );
    }

    private Location transformPoint(Location origin, GameMap.IntPoint point, MapRotation rotation, WorldManager.SchematicMetadata metadata) {
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

    private BoundingBox transformRegion(
        Location origin,
        GameMap.IntRegion region,
        MapRotation rotation,
        WorldManager.SchematicMetadata metadata
    ) {
        int[] xs = { region.min().x(), region.max().x() };
        int[] ys = { region.min().y(), region.max().y() };
        int[] zs = { region.min().z(), region.max().z() };

        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (int x : xs) {
            for (int z : zs) {
                Location transformed = transformPoint(origin, new GameMap.IntPoint(x, 0, z), rotation, metadata);
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
