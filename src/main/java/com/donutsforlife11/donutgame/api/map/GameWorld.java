package com.donutsforlife11.donutgame.api.map;

import org.bukkit.World;

public class GameWorld {
    private final String id;
    private World bukkitWorld;

    public GameWorld(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        this.id = id;
    }

    public String id() {
        return id;
    }

    public World bukkitWorld() {
        return bukkitWorld;
    }

    public void setBukkitWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }
}
