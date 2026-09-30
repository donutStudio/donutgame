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
import org.bukkit.damage.DamageSource;
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
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.ItemSpec;
import com.donutsforlife11.donutgame.api.player.PlayerAttributeDefaults;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public interface GameEntityBase {
    GameWorld world();

    UUID uuid();

    Entity bukkitEntity();

    default GameLocation location() {
        Entity entity = bukkitEntity();
        return entity == null ? null : new GameLocation(world(), entity.getLocation());
    }

    default CompletableFuture<Boolean> teleport(GameLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        GameWorld targetWorld = location.world() == null ? world() : location.world();
        if (targetWorld == null || targetWorld.bukkitWorld() == null) {
            throw new IllegalStateException("Entity " + uuid() + " is not in a loaded GameWorld.");
        }
        return requireEntity().teleportAsync(location.toBukkit(targetWorld.bukkitWorld()));
    }

    default void setHealth(float health) {
        LivingEntity entity = requireLivingEntity();
        entity.setHealth(Math.min(Math.max(0.0, health), entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    default void heal() {
        LivingEntity entity = requireLivingEntity();
        entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    default void heal(float amount) {
        LivingEntity entity = requireLivingEntity();
        setHealth((float) (entity.getHealth() + Math.max(0.0f, amount)));
    }

    default void damage(float amount) {
        requireLivingEntity().damage(amount);
    }

    default void damage(float amount, DamageSource source) {
        if (source == null) {
            damage(amount);
            return;
        }
        requireLivingEntity().damage(amount, source);
    }

    default float health() {
        return (float) requireLivingEntity().getHealth();
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
        Entity entity = bukkitEntity();
        return entity == null || entity.isDead();
    }

    default void setVelocity(Vector velocity) {
        requireEntity().setVelocity(velocity == null ? new Vector() : velocity.clone());
    }

    default Vector velocity() {
        return requireEntity().getVelocity().clone();
    }

    default void setCustomName(Component name) {
        requireEntity().customName(name);
    }

    default void setCustomNameVisible(boolean visible) {
        requireEntity().setCustomNameVisible(visible);
    }

    default Component customName() {
        return requireEntity().customName();
    }

    default boolean customNameVisible() {
        return requireEntity().isCustomNameVisible();
    }

    default void setInvulnerable(boolean invulnerable) {
        requireEntity().setInvulnerable(invulnerable);
    }

    default boolean isInvulnerable() {
        return requireEntity().isInvulnerable();
    }

    default void setInvisible(boolean invisible) {
        requireEntity().setInvisible(invisible);
    }

    default boolean isInvisible() {
        return requireEntity().isInvisible();
    }

    default void setNoGravity(boolean noGravity) {
        requireEntity().setGravity(!noGravity);
    }

    default boolean hasNoGravity() {
        return !requireEntity().hasGravity();
    }

    default void setSilent(boolean silent) {
        requireEntity().setSilent(silent);
    }

    default boolean isSilent() {
        return requireEntity().isSilent();
    }

    default void setFireTicks(int ticks) {
        requireEntity().setFireTicks(ticks);
    }

    default int fireTicks() {
        return requireEntity().getFireTicks();
    }

    default boolean setGlowing(boolean glowing) {
        Entity entity = requireEntity();
        GlowService glowService = glowService();
        if (glowService == null) {
            entity.setGlowing(glowing);
            return true;
        }
        return glowService.setGlowing(entity, glowing, (NamedTextColor) null);
    }

    default boolean setGlowing(boolean glowing, Collection<GamePlayer> viewers) {
        Entity entity = requireEntity();
        GlowService glowService = glowService();
        if (glowService == null) {
            entity.setGlowing(false);
            return !glowing;
        }
        return glowService.setGlowing(entity, glowing, viewers, null);
    }

    default boolean setGlowing(boolean glowing, Supplier<Collection<GamePlayer>> viewers) {
        Entity entity = requireEntity();
        GlowService glowService = glowService();
        if (glowService == null) {
            entity.setGlowing(false);
            return !glowing;
        }
        return glowService.setGlowing(entity, glowing, viewers, null);
    }

    default void setGlowColor(NamedTextColor color) {
        Entity entity = requireEntity();
        GlowService glowService = glowService();
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
        addEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    default void addEffect(PotionEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect cannot be null");
        }
        requireLivingEntity().addPotionEffect(effect);
    }

    default void clearEffects() {
        requireLivingEntity().clearActivePotionEffects();
    }

    default void clearEffect(PotionEffectType effect) {
        if (effect != null) {
            requireLivingEntity().removePotionEffect(effect);
        }
    }

    default void clearEffect(PotionEffect effect) {
        if (effect != null) {
            clearEffect(effect.getType());
        }
    }

    default boolean hasEffect(PotionEffectType effect) {
        return effect != null && requireLivingEntity().hasPotionEffect(effect);
    }

    default boolean hasEffect(PotionEffect effect) {
        return effect != null && hasEffect(effect.getType());
    }

    default List<PotionEffect> effects() {
        return List.copyOf(requireLivingEntity().getActivePotionEffects());
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

    default void setItem(EquipmentSlot slot, ItemSpec item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        setItem(slot, item.createItem());
    }

    default void setItem(EquipmentSlot slot, GameItem item) {
        requireEquipment().setItem(requireSlot(slot), item == null ? null : item.copyBukkitItem());
    }

    default void clearItems() {
        clearEquipment(null);
        if (requireEntity() instanceof org.bukkit.entity.HumanEntity human) {
            human.getInventory().clear();
        }
    }

    default void clearItems(ItemSpec item) {
        if (item == null) {
            clearItems();
            return;
        }
        clearEquipment(item::matches);
        if (requireEntity() instanceof org.bukkit.entity.HumanEntity human) {
            clearInventory(human.getInventory(), item::matches);
        }
    }

    default void clearItems(GameItem item) {
        if (item == null) {
            clearItems();
            return;
        }
        ItemStack bukkitItem = item.bukkitItem();
        clearEquipment(stack -> stack != null && stack.isSimilar(bukkitItem));
        if (requireEntity() instanceof org.bukkit.entity.HumanEntity human) {
            clearInventory(human.getInventory(), stack -> stack != null && stack.isSimilar(bukkitItem));
        }
    }

    default GameItem getItem(EquipmentSlot slot) {
        ItemStack item = requireEquipment().getItem(requireSlot(slot));
        return GameItem.from(item);
    }

    private Entity requireEntity() {
        Entity entity = bukkitEntity();
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

    private EquipmentSlot requireSlot(EquipmentSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        return slot;
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

    private GlowService glowService() {
        try {
            return JavaPlugin.getPlugin(Donutgame.class).glowService();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
