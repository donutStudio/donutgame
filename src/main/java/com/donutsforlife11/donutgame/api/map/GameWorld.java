package com.donutsforlife11.donutgame.api.map;

import org.bukkit.World;

public class GameWorld {
    private World bukkitWorld;

    public GameWorld(World bukkitWorld) {
        this.bukkitWorld = bukkitWorld;
    }

    public World getBukkitWorld() {
        return bukkitWorld;
    }
}
