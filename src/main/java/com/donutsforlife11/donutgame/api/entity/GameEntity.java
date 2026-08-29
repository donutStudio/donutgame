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
            instance.setBaseValue(defaultAttributeBase(attribute));
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
            if (instance != null) instance.setBaseValue(defaultAttributeBase(attribute));
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

    private double defaultAttributeBase(Attribute attribute) {
        if (attribute == Attribute.MAX_HEALTH) return 20.0;
        if (attribute == Attribute.FOLLOW_RANGE) return 32.0;
        if (attribute == Attribute.KNOCKBACK_RESISTANCE) return 0.0;
        if (attribute == Attribute.MOVEMENT_SPEED) return 0.1;
        if (attribute == Attribute.FLYING_SPEED) return 0.4;
        if (attribute == Attribute.ATTACK_DAMAGE) return 1.0;
        if (attribute == Attribute.ATTACK_KNOCKBACK) return 0.0;
        if (attribute == Attribute.ATTACK_SPEED) return 4.0;
        if (attribute == Attribute.ARMOR) return 0.0;
        if (attribute == Attribute.ARMOR_TOUGHNESS) return 0.0;
        if (attribute == Attribute.LUCK) return 0.0;
        if (attribute == Attribute.JUMP_STRENGTH) return 0.42;
        if (attribute == Attribute.OXYGEN_BONUS) return 0.0;
        if (attribute == Attribute.BURNING_TIME) return 1.0;
        if (attribute == Attribute.EXPLOSION_KNOCKBACK_RESISTANCE) return 0.0;
        if (attribute == Attribute.MOVEMENT_EFFICIENCY) return 0.0;
        if (attribute == Attribute.WATER_MOVEMENT_EFFICIENCY) return 0.0;
        if (attribute == Attribute.BLOCK_BREAK_SPEED) return 1.0;
        if (attribute == Attribute.SUBMERGED_MINING_SPEED) return 0.2;
        if (attribute == Attribute.ENTITY_INTERACTION_RANGE) return 3.0;
        if (attribute == Attribute.BLOCK_INTERACTION_RANGE) return 4.5;
        if (attribute == Attribute.SAFE_FALL_DISTANCE) return 3.0;
        if (attribute == Attribute.FALL_DAMAGE_MULTIPLIER) return 1.0;
        if (attribute == Attribute.SNEAKING_SPEED) return 0.3;
        if (attribute == Attribute.MINING_EFFICIENCY) return 0.0;
        if (attribute == Attribute.SWEEPING_DAMAGE_RATIO) return 0.0;
        if (attribute == Attribute.SCALE) return 1.0;
        if (attribute == Attribute.STEP_HEIGHT) return 0.6;
        if (attribute == Attribute.GRAVITY) return 0.08;
        if (attribute == Attribute.CAMERA_DISTANCE) return 4.0;
        return 0.0;
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
