package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;

import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.MapRegistry;
import com.donutsforlife11.donutgame.internal.map.WorldRegistry;

public class MapManager {
    public static final String DEFAULT_MAP_ID = "donutgame_default";

    private final MapRegistry mapRegistry;
    private final WorldRegistry worldRegistry;
    private final String gameId;
    private final int gameIndex;
    private volatile GameWorld currentWorld;

    public MapManager(MapRegistry mapRegistry, WorldRegistry worldRegistry, String gameId, int gameIndex) {
        this.mapRegistry = mapRegistry;
        this.worldRegistry = worldRegistry;
        this.gameId = gameId;
        this.gameIndex = gameIndex;
    }

    public CompletableFuture<GameMap> setMap(GameMap map) {
        Objects.requireNonNull(map, "Map is null!");
        GameMapDescriptor descriptor = mapRegistry.getGameMapDescriptor(map.id());
        GameMap gameMap = mapRegistry.loadGameMap(descriptor);
        GameMapDescriptor templateDescriptor = descriptor.backingType() == BackingType.WORLD
            ? descriptor
            : mapRegistry.getGameMapDescriptor(DEFAULT_MAP_ID);
        if (templateDescriptor.backingType() != BackingType.WORLD) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Default map " + DEFAULT_MAP_ID + " must be slime-backed.")
            );
        }
        String instanceWorldName = gameId + "_" + gameIndex + "_" + gameMap.id();
        return worldRegistry
            .loadSlimeWorld(templateDescriptor.runtimeAssetPath(), instanceWorldName)
            .thenApply(world -> {
                currentWorld = new GameWorld(world);
                return gameMap;
            });
    }

    public GameWorld currentWorld() {
        return currentWorld;
    }
}