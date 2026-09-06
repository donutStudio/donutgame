package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.internal.map.GameWorld;

public abstract class GameModule {
    public static final String DEFAULT_WORLD_ID = "default";

    private String id;
    private String name;
    private YamlConfiguration config;
    private final Map<String, GameWorld> worlds = new LinkedHashMap<>();

    protected GameModule() {
        worlds.put(DEFAULT_WORLD_ID, new GameWorld(DEFAULT_WORLD_ID));
    }

    protected final void initialize(GameModuleDescriptor descriptor, YamlConfiguration config) {
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.config = config;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public YamlConfiguration config() {
        return config;
    }

    protected final Map<String, GameWorld> worlds() {
        return Collections.unmodifiableMap(worlds);
    }

    protected final GameWorld defaultWorld() {
        return worlds.get(DEFAULT_WORLD_ID);
    }

    protected final void putWorld(GameWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        worlds.put(world.id(), world);
    }
}
