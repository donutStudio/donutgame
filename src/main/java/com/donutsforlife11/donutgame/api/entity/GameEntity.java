package com.donutsforlife11.donutgame.api.entity;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;

public class GameEntity {
    private final GameWorld world;
    private final UUID uuid;
    private final EntityType type;

    public GameEntity(GameWorld world, UUID uuid, EntityType type) {
        this.world = Objects.requireNonNull(world);
        this.uuid = Objects.requireNonNull(uuid);
        this.type = Objects.requireNonNull(type);
    }

    public boolean exists() {
        return bukkitEntity() != null;
    }

    public GameLocation location() {
        return GameLocation.fromBukkit(requireEntity().getLocation());
    }

    public boolean teleport(GameLocation location) {
        return requireEntity().teleport(location.toBukkit(world.bukkitWorld()));
    }

    public void remove() {
        requireEntity().remove();
    }

    public void kill() {
        Entity entity = requireEntity();
        if (entity instanceof Damageable damageable) {
            damageable.setHealth(0);
            return;
        }
        entity.remove();
    }

    public void setHealth(double health) {
        if (requireEntity() instanceof Damageable damageable) {
            damageable.setHealth(Math.max(0, health));
        }
    }

    public void damage(double damage) {
        if (requireEntity() instanceof Damageable damageable) {
            damageable.damage(damage);
        }
    }

    public void addEffect(PotionEffect effect) {
        if (requireEntity() instanceof org.bukkit.entity.LivingEntity livingEntity) {
            livingEntity.addPotionEffect(effect);
        }
    }

    public void setAttributeBaseValue(Attribute attribute, double value) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        }
    }

    public Double getAttributeValue(Attribute attribute) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return null;
    }

    public PersistentDataContainer persistentData() {
        return requireEntity().getPersistentDataContainer();
    }

    public <P, C> void setData(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        persistentData().set(key, type, value);
    }

    public <T extends Entity> T as(Class<T> entityClass) {
        Entity entity = requireEntity();
        if (!entityClass.isInstance(entity)) {
            throw new IllegalStateException("Entity " + uuid + " is not a " + entityClass.getSimpleName());
        }
        return entityClass.cast(entity);
    }

    public Entity bukkitEntity() {
        Entity entity = world.bukkitWorld().getEntity(uuid);
        return entity != null && entity.getType() == type ? entity : null;
    }

    protected Entity requireEntity() {
        Entity entity = bukkitEntity();
        if (entity == null) {
            throw new IllegalStateException("Entity is no longer present in the game world.");
        }
        return entity;
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

    @Override
    public boolean equals(Object object) {
        return object instanceof GameEntity other && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
