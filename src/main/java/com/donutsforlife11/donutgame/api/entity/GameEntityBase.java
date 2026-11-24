package com.donutsforlife11.donutgame.api.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemType;
import com.donutsforlife11.donutgame.api.player.PlayerAttributeDefaults;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.ui.GlowService;
import com.donutsforlife11.donutgame.internal.game.GameScope;

import net.kyori.adventure.text.format.NamedTextColor;

public interface GameEntityBase extends PaperEntityMethods {
    GameWorld world();

    UUID uuid();

    /** Current world-space hitbox, including entity scale. */
    default org.bukkit.util.BoundingBox getBoundingBox() { return requireEntity().getBoundingBox(); }

    default void setAI(boolean enabled) {
        if (requireEntity() instanceof org.bukkit.entity.Mob mob) mob.setAI(enabled);
    }

    /** Disable autonomous behavior while preserving movement physics. */
    default void setAware(boolean aware) {
        if (requireEntity() instanceof org.bukkit.entity.Mob mob) mob.setAware(aware);
    }

    default void setCollidable(boolean collidable) { com.donutsforlife11.donutgame.internal.game.EntityCollisions.set(world(), requireLivingEntity(), collidable); }

    default void setVisibleTo(GamePlayer viewer, boolean visible) {
        GameScope.requirePlayer(world().owner(), viewer);
        var player = com.donutsforlife11.donutgame.api.player.GamePlayerHandle.bukkitPlayer(viewer);
        if (player == null) return;
        var plugin = JavaPlugin.getPlugin(Donutgame.class);
        if (visible) player.showEntity(plugin, requireEntity()); else player.hideEntity(plugin, requireEntity());
    }

    default void setSize(int size) {
        if (requireEntity() instanceof org.bukkit.entity.Slime slime) slime.setSize(size);
        else throw new IllegalStateException("Entity is not a slime.");
    }

    default boolean addPassenger(GameEntityBase passenger) {
        GameScope.requireLocation(world(), passenger.getLocation());
        return requireEntity().addPassenger(GameEntityHandle.requireBukkitEntity(passenger));
    }

    default void setExplosionPower(int power) {
        if (requireEntity() instanceof org.bukkit.entity.Fireball fireball) {
            fireball.setYield(power);
            fireball.setIsIncendiary(false);
        }
    }

    default void setAcceleration(Vector acceleration) {
        if (requireEntity() instanceof org.bukkit.entity.Fireball fireball) fireball.setAcceleration(acceleration.clone());
    }

    default void setShowingBottom(boolean showing) {
        if (requireEntity() instanceof org.bukkit.entity.EnderCrystal crystal) crystal.setShowingBottom(showing);
    }

    default CompletableFuture<Boolean> teleport(GameLocation location) {
        GameWorld world = world();
        var destination = GameLocationHandle.toBukkit(location, world);
        long generation = GameWorldHandle.generation(world);
        return requireEntity().teleportAsync(destination).thenApply(moved -> moved
            && GameWorldHandle.generation(world) == generation && GameEntityHandle.bukkitEntity(this) != null);
    }

    default void setHealth(double health) {
        LivingEntity entity = requireLivingEntity();
        entity.setHealth(Math.min(Math.max(0.0, health), entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    default void heal() {
        LivingEntity entity = requireLivingEntity();
        entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    default void heal(float amount) {
        LivingEntity entity = requireLivingEntity();
        setHealth(entity.getHealth() + Math.max(0.0f, amount));
    }

    default void kill() {
        Entity entity = requireEntity();
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setHealth(0.0);
            return;
        }
        entity.remove();
    }

    default boolean isDead() {
        Entity entity = GameEntityHandle.bukkitEntity(this);
        return entity == null || entity.isDead();
    }

    default void setRotation(float yaw, float pitch) {
        setRotation(yaw, pitch, 0);
    }

    default void setRotation(float yaw, float pitch, int ticks) {
        JavaPlugin.getPlugin(Donutgame.class).npcService().rotate(this, yaw, pitch, ticks);
    }

    default void setRotationTowards(GameLocation location) {
        setRotationTowards(location, 0);
    }

    default void setRotationTowards(GameLocation location, int ticks) {
        GameScope.requireLocation(world(), location);
        Entity entity = requireEntity();
        org.bukkit.Location eye = entity instanceof LivingEntity living ? living.getEyeLocation() : entity.getLocation();
        Vector direction = new Vector(location.x() - eye.getX(), location.y() - eye.getY(), location.z() - eye.getZ());
        if (direction.lengthSquared() > 1.0E-12) {
            eye.setDirection(direction);
            setRotation(eye.getYaw(), eye.getPitch(), ticks);
        }
    }

    default boolean setGlowing(boolean glowing) {
        Entity entity = requireEntity();
        GlowService glowService = JavaPlugin.getPlugin(Donutgame.class).glowService();
        if (glowService == null) {
            entity.setGlowing(glowing);
            return true;
        }
        return glowService.setGlowing(entity, glowing, (NamedTextColor) null);
    }

    default boolean setGlowing(boolean glowing, Collection<GamePlayer> viewers) {
        Entity entity = requireEntity();
        GlowService glowService = JavaPlugin.getPlugin(Donutgame.class).glowService();
        if (glowService == null) {
            entity.setGlowing(false);
            return !glowing;
        }
        return glowService.setGlowing(entity, glowing, GameScope.requirePlayers(world().owner(), viewers), null);
    }

    default boolean setGlowing(boolean glowing, Supplier<Collection<GamePlayer>> viewers) {
        Entity entity = requireEntity();
        GlowService glowService = JavaPlugin.getPlugin(Donutgame.class).glowService();
        if (glowService == null) {
            entity.setGlowing(false);
            return !glowing;
        }
        return glowService.setGlowing(entity, glowing, () -> GameScope.requirePlayers(world().owner(), viewers.get()), null);
    }

    default void setGlowColor(NamedTextColor color) {
        Entity entity = requireEntity();
        GlowService glowService = JavaPlugin.getPlugin(Donutgame.class).glowService();
        if (glowService == null) {
            return;
        }
        glowService.setGlowColor(entity, color);
    }

    default void addEffect(PotionEffectType effect, int ticks, int amplifier) {
        addEffect(effect, ticks, amplifier, false);
    }

    default void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) {
        if (effect == null) {
            throw new IllegalArgumentException("effect cannot be null");
        }
        addPotionEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    default void setAttributeBase(Attribute attribute, double value) {
        requireAttribute(attribute).setBaseValue(value);
    }

    default void resetAttributeBase(Attribute attribute) {
        AttributeInstance instance = requireAttribute(attribute);
        PlayerAttributeDefaults.restoreVanillaBase(instance);
    }

    default double attributeBase(Attribute attribute) {
        return requireAttribute(attribute).getBaseValue();
    }

    default void addModifier(Attribute attribute, AttributeModifier modifier) {
        if (modifier == null) {
            throw new IllegalArgumentException("modifier cannot be null");
        }
        requireAttribute(attribute).addModifier(modifier);
    }

    default void removeModifier(Attribute attribute, AttributeModifier modifier) {
        if (modifier != null) {
            requireAttribute(attribute).removeModifier(modifier);
        }
    }

    default double attributeModifierValue(Attribute attribute, AttributeModifier modifier) {
        if (modifier == null) {
            throw new IllegalArgumentException("modifier cannot be null");
        }
        return requireAttribute(attribute).getModifiers().contains(modifier) ? modifier.getAmount() : 0.0;
    }

    default List<AttributeModifier> attributeModifiers(Attribute attribute) {
        return List.copyOf(requireAttribute(attribute).getModifiers());
    }

    default double attributeValue(Attribute attribute) {
        return requireAttribute(attribute).getValue();
    }

    default void setItem(EquipmentSlot slot, GameItemType item) {
        setItem(slot, item == null ? null : item.create());
    }

    default void setItem(EquipmentSlot slot, GameItem item) {
        requireEquipment().setItem(java.util.Objects.requireNonNull(slot, "slot"), item == null ? null : item.copyBukkitItem());
    }

    default void clearItems() {
        clearEquipment(null);
        if (requireEntity() instanceof org.bukkit.entity.HumanEntity human) {
            human.getInventory().clear();
        }
    }

    default void clearItems(GameItem item) {
        if (item == null) {
            clearItems();
            return;
        }
        ItemStack bukkitItem = item.copyBukkitItem();
        clearEquipment(stack -> stack != null && stack.isSimilar(bukkitItem));
        if (requireEntity() instanceof org.bukkit.entity.HumanEntity human) {
            clearInventory(human.getInventory(), stack -> stack != null && stack.isSimilar(bukkitItem));
        }
    }

    default GameItem getItem(EquipmentSlot slot) {
        ItemStack item = requireEquipment().getItem(java.util.Objects.requireNonNull(slot, "slot"));
        return GameItem.from(item);
    }

    private Entity requireEntity() {
        Entity entity = GameEntityHandle.bukkitEntity(this);
        if (entity == null) {
            throw new IllegalStateException("Entity " + uuid() + " is not loaded.");
        }
        return entity;
    }

    private LivingEntity requireLivingEntity() {
        Entity entity = requireEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            throw new IllegalStateException("Entity " + uuid() + " is not a living entity.");
        }
        return livingEntity;
    }

    private EntityEquipment requireEquipment() {
        EntityEquipment equipment = requireLivingEntity().getEquipment();
        if (equipment == null) {
            throw new IllegalStateException("Entity " + uuid() + " does not have equipment.");
        }
        return equipment;
    }

    private AttributeInstance requireAttribute(Attribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        AttributeInstance instance = requireLivingEntity().getAttribute(attribute);
        if (instance == null) {
            throw new IllegalArgumentException("Entity " + uuid() + " does not have attribute " + attribute + ".");
        }
        return instance;
    }

    private void clearEquipment(Predicate<ItemStack> matcher) {
        EntityEquipment equipment = requireEquipment();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack;
            try {
                stack = equipment.getItem(slot);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (matcher == null || matcher.test(stack)) {
                equipment.setItem(slot, null);
            }
        }
    }

    private void clearInventory(Inventory inventory, Predicate<ItemStack> matcher) {
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < inventory.getSize(); index++) {
            if (matcher.test(inventory.getItem(index))) {
                slots.add(index);
            }
        }
        for (int slot : slots) {
            inventory.clear(slot);
        }
    }


}
