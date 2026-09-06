package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

public abstract class GameModule {
    public static final String DEFAULT_WORLD_ID = "default";

    private Donutgame plugin;
    private MapManager mapManager;
    private PlayerManager playerManager;
    private String id;
    private String name;
    private int index;
    private YamlConfiguration config;
    private volatile ModuleLifecycleState lifecycleState = ModuleLifecycleState.NEW;
    private final Map<String, GameWorld> worlds = new LinkedHashMap<>();

    protected GameModule() {
        worlds.put(DEFAULT_WORLD_ID, new GameWorld(DEFAULT_WORLD_ID));
    }

    protected final void initialize(
        Donutgame plugin,
        GameModuleDescriptor descriptor,
        int index,
        YamlConfiguration config,
        MapManager mapManager,
        PlayerManager playerManager
    ) {
        this.plugin = plugin;
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.index = index;
        this.config = config;
        this.mapManager = mapManager;
        this.playerManager = playerManager;
    }

    public void beforeLoad() {
        String explicitMap = config.getString("map");
        if (explicitMap != null && !explicitMap.isBlank()) {
            mapManager.setMap(explicitMap);
            return;
        }

        List<String> maps = config.getStringList("maps");
        if (!maps.isEmpty()) {
            mapManager.setMap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        }
    }

    public void onLoad() {
    }

    public void onUnload() {
    }

    protected final CompletableFuture<GameModule> startLoadSequence() {
        try {
            requireState(ModuleLifecycleState.NEW);
            setLifecycleState(ModuleLifecycleState.LOADING);
            beforeLoad();
            return mapManager.whenReady()
                .thenApply(ignored -> {
                    try {
                        onLoad();
                        setLifecycleState(ModuleLifecycleState.LOADED);
                        return this;
                    } catch (Throwable throwable) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                        throw new RuntimeException(throwable);
                    }
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                    }
                });
        } catch (Throwable throwable) {
            setLifecycleState(ModuleLifecycleState.FAILED);
            return CompletableFuture.failedFuture(throwable);
        }
    }

    protected final CompletableFuture<Void> shutdown() {
        setLifecycleState(ModuleLifecycleState.UNLOADING);
        return playerManager.clear()
            .thenCompose(ignored -> mapManager.unloadWorlds())
            .whenComplete((ignored, throwable) -> setLifecycleState(throwable == null ? ModuleLifecycleState.UNLOADED : ModuleLifecycleState.FAILED));
    }

    public final Donutgame plugin() {
        return plugin;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int index() {
        return index;
    }

    public YamlConfiguration config() {
        return config;
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public PlayerManager playerManager() {
        return playerManager;
    }

    public final Map<String, GameWorld> worlds() {
        return Collections.unmodifiableMap(worlds);
    }

    public final GameWorld defaultWorld() {
        return worlds.get(DEFAULT_WORLD_ID);
    }

    protected final void putWorld(GameWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        worlds.put(world.id(), world);
    }

    public final ModuleLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public final void log(String message) {
        plugin.getLogger().info("[" + id + ":" + index + "] " + message);
    }

    public final void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, "[" + id + ":" + index + "] " + message, throwable);
    }

    private void requireState(ModuleLifecycleState expected) {
        if (lifecycleState != expected) {
            throw new IllegalStateException("Module " + id + " expected lifecycle state " + expected + " but was " + lifecycleState + ".");
        }
    }

    private void setLifecycleState(ModuleLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }
}
