package com.donutsforlife11.donutgame.api.entity;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;

public class GameEntity implements GameEntityBase {
    private final GameWorld world;
    private final long generation;
    private final UUID uuid;
    private final EntityType type;
    private WeakReference<Entity> entityRef;

    GameEntity(GameWorld world, Entity entity) {
        this(world, GameWorldHandle.generation(world), entity);
    }

    protected GameEntity(GameWorld world, long generation, Entity entity) {
        this.world = Objects.requireNonNull(world, "world");
        this.generation = generation;
        this.uuid = entity.getUniqueId();
        this.type = entity.getType();
        this.entityRef = new WeakReference<>(entity);
    }

    @Override public void remove() {
        var entity = entityRef.get();
        if(entity != null && GameWorldHandle.generation(world) == generation) entity.remove();
    }

    public final GameWorld world() {
        return world;
    }

    public final UUID uuid() {
        return uuid;
    }

    public EntityType getType() {
        return type;
    }

    Entity bukkitEntity() {
        World bukkitWorld = GameWorldHandle.bukkitWorld(world);
        if (bukkitWorld == null || !world.owner().acceptsLiveOperations() || GameWorldHandle.generation(world) != generation) {
            return null;
        }
        Entity cached = entityRef.get();
        if (cached != null && !cached.isDead() && cached.getWorld() == bukkitWorld) {
            return cached;
        }
        Entity entity = bukkitWorld.getEntity(uuid);
        if (entity == null || entity.getWorld() != bukkitWorld || entity.getType() != type) {
            return null;
        }
        entityRef = new WeakReference<>(entity);
        return entity;
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof GameEntity entity
            && world == entity.world && generation == entity.generation && uuid.equals(entity.uuid);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(world, generation, uuid);
    }
}
