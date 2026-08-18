package com.donutsforlife11.donutgame.api.entity;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;

public class GameEntity {
    private static final int DEFAULT_EFFECT_DURATION_TICKS = 30 * 20;
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

    public boolean teleport(double x, double y, double z) {
        return teleport(new GameLocation(x, y, z));
    }

    public boolean teleport(double x, double y, double z, double pitch, double yaw) {
        return teleport(new GameLocation(x, y, z, pitch, yaw));
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

    public void heal() {
        if (requireEntity() instanceof Damageable damageable) {
            damageable.setHealth(Math.min(maxHealth(damageable), maxHealth(damageable)));
        }
    }

    public void heal(double health) {
        if (requireEntity() instanceof Damageable damageable) {
            damageable.setHealth(Math.min(damageable.getHealth() + health, maxHealth(damageable)));
        }
    }

    public void addEffect(PotionEffectType effect) {
        addEffect(effect, DEFAULT_EFFECT_DURATION_TICKS);
    }

    public void addEffect(PotionEffectType effect, int ticks) {
        addEffect(effect, ticks, 0);
    }

    public void addEffect(PotionEffectType effect, int ticks, int amplifier) {
        addEffect(effect, ticks, amplifier, false);
    }

    public void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null.");
        if (ticks < 0 && ticks != PotionEffect.INFINITE_DURATION) throw new IllegalArgumentException("Effect duration cannot be negative.");
        if (amplifier < 0) throw new IllegalArgumentException("Effect amplifier cannot be negative.");
        addEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    public void addEffect(PotionEffect effect) {
        if (requireEntity() instanceof LivingEntity livingEntity) {
            livingEntity.addPotionEffect(effect);
        }
    }

    public double getAttribute(Attribute attribute) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return 0.0;
    }

    public void resetAttribute(Attribute attribute) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance == null) return;
            instance.setBaseValue(instance.getDefaultValue());
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) instance.removeModifier(modifier);
        }
    }

    public void setAttributeBase(Attribute attribute, double value) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        }
    }

    public double getAttributeBase(Attribute attribute) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) {
                return instance.getBaseValue();
            }
        }
        return 0.0;
    }

    public void resetAttributeBase(Attribute attribute) {
        if (requireEntity() instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(attribute);
            if (instance != null) instance.setBaseValue(instance.getDefaultValue());
        }
    }

    public void clearEffects(PotionEffectType effect) {
        if (requireEntity() instanceof LivingEntity livingEntity) livingEntity.removePotionEffect(effect);
    }

    public void clearEffects() {
        if (requireEntity() instanceof LivingEntity livingEntity) {
            for (PotionEffect effect : livingEntity.getActivePotionEffects()) livingEntity.removePotionEffect(effect.getType());
        }
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

    private double maxHealth(Damageable damageable) {
        if (damageable instanceof Attributable attributable) {
            AttributeInstance instance = attributable.getAttribute(Attribute.MAX_HEALTH);
            if (instance != null) return instance.getValue();
        }
        return 20.0;
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
