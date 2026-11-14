package com.donutsforlife11.donutgame.api.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.Donutgame;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
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

    public static GameEntityBase wrap(GameWorld world, Entity entity) {
        if (entity == null) {
            return null;
        }
        if (GameWorldHandle.bukkitWorld(world) != entity.getWorld()) {
            throw new IllegalArgumentException("Entity is outside the game world.");
        }
        if (entity instanceof Player player) {
            return world.owner().playerManager().getPlayer(player.getUniqueId());
        }
        var npc = JavaPlugin.getPlugin(Donutgame.class).npcService().find(world, entity);
        return npc != null ? npc : create(world, entity);
    }

    public static GameEntity create(GameWorld world, Entity entity) {
        if (GameWorldHandle.bukkitWorld(world) != entity.getWorld()) {
            throw new IllegalArgumentException("Entity is outside the game world.");
        }
        return new GameEntity(world, entity);
    }
}
