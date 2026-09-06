package com.donutsforlife11.donutgame.api.entity;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;

public class GameEntity {
    private final GameWorld world;
    private final UUID uuid;
    private final EntityType type;

    public GameEntity(GameWorld world, Entity entity) {
        this(world, entity.getUniqueId(), entity.getType());
    }

    public GameEntity(GameWorld world, UUID uuid, EntityType type) {
        this.world = Objects.requireNonNull(world, "world");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.type = Objects.requireNonNull(type, "type");
    }

    public GameWorld world() {
        return world;
    }

    public UUID uuid() {
        return uuid;
    }

    public EntityType type() {
        return type;
    }

    public Entity bukkitEntity() {
        if (world.bukkitWorld() == null) {
            return null;
        }
        Entity entity = world.bukkitWorld().getEntity(uuid);
        return entity != null && entity.getType() == type ? entity : null;
    }

    public GameLocation location() {
        Entity entity = bukkitEntity();
        return entity == null ? null : new GameLocation(world, entity.getLocation());
    }
}
