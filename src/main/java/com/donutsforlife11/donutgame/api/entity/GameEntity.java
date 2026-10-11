package com.donutsforlife11.donutgame.api.entity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.object.GameEntityType;

public class GameEntity implements GameEntityBase {
    private final GameWorld world;
    private final long generation;
    private final UUID uuid;
    private final EntityType type;

    GameEntity(GameWorld world, Entity entity) {
        this(world, GameWorldHandle.generation(world), entity);
    }

    GameEntity(GameWorld world, long generation, Entity entity) {
        this(world, generation, entity.getUniqueId(), entity.getType());
    }

    public GameEntity(GameWorld world, UUID uuid, EntityType type) {
        this(world, GameWorldHandle.generation(world), uuid, type);
    }

    GameEntity(GameWorld world, long generation, UUID uuid, EntityType type) {
        this.world = Objects.requireNonNull(world, "world");
        this.generation = generation;
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

    Entity bukkitEntity() {
        org.bukkit.World bukkitWorld = GameWorldHandle.requireBukkitWorld(world, generation);
        if (bukkitWorld == null) {
            return null;
        }
        Entity entity = bukkitWorld.getEntity(uuid);
        return entity != null && entity.getType() == type ? entity : null;
    }

    public boolean is(Class<? extends Entity> entityClass) {
        Entity entity = bukkitEntity();
        return entityClass != null && entityClass.isInstance(entity);
    }

    public boolean is(EntityType entityType) {
        return entityType != null && type == entityType;
    }

    public boolean is(GameEntityType entityType) {
        return entityType != null && is(entityType.entityType());
    }

    public <E extends Entity> boolean editEntity(Class<E> entityClass, Consumer<? super E> editor) {
        Objects.requireNonNull(entityClass, "entityClass");
        Objects.requireNonNull(editor, "editor");
        Entity entity = bukkitEntity();
        if (!entityClass.isInstance(entity)) {
            return false;
        }
        editor.accept(entityClass.cast(entity));
        return true;
    }
}
