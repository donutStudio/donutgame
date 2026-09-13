package com.donutsforlife11.donutgame.api.entity;

import java.util.UUID;

import org.bukkit.entity.Entity;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;

public interface GameEntityBase {
    GameWorld world();

    UUID uuid();

    Entity bukkitEntity();

    default GameLocation location() {
        Entity entity = bukkitEntity();
        return entity == null ? null : new GameLocation(world(), entity.getLocation());
    }
}
