package com.donutsforlife11.donutgame.api.entity;

import org.bukkit.entity.Entity;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should use scoped entity operations instead.
 */
public final class GameEntityHandle {
    private GameEntityHandle() {
    }

    public static Entity bukkitEntity(GameEntityBase entity) {
        if (entity instanceof GameEntity gameEntity) {
            return gameEntity.bukkitEntity();
        }
        if (entity instanceof GamePlayer player) {
            return GamePlayerHandle.bukkitEntity(player);
        }
        return null;
    }

    public static Entity requireBukkitEntity(GameEntityBase entity) {
        Entity bukkitEntity = bukkitEntity(entity);
        if (bukkitEntity == null) {
            throw new IllegalStateException("Entity " + (entity == null ? "<null>" : entity.uuid()) + " is not loaded.");
        }
        return bukkitEntity;
    }

    public static GameEntity create(GameWorld world, long generation, Entity entity) {
        return new GameEntity(world, generation, entity);
    }

    public static GameEntity create(GameWorld world, Entity entity) {
        return new GameEntity(world, entity);
    }
}
