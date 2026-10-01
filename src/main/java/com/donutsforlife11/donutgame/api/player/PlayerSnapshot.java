package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.GameMode;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

final class PlayerSnapshot {
    ItemStack[] inventory = new ItemStack[36];
    ItemStack[] armor = new ItemStack[4];
    ItemStack[] extra = new ItemStack[1];
    final Collection<PotionEffect> effects = new ArrayList<>();
    final Map<Attribute, Double> attributeBases = new HashMap<>();
    final Map<Attribute, List<AttributeModifier>> attributeModifiers = new HashMap<>();
    double health = 20.0;
    int foodLevel = 20;
    float saturation = 20.0f;
    int arrowsInBody;
    int fireTicks;
    int level;
    float exp;
    int totalExperience;
    GameMode gameMode = GameMode.SURVIVAL;
    boolean invulnerable;
    boolean invisible;
    boolean canPickupItems = true;
    boolean allowFlight;
    boolean flying;
    private long lastTimedUpdateMillis = System.currentTimeMillis();

    static PlayerSnapshot empty() {
        return new PlayerSnapshot();
    }

    void capture(org.bukkit.entity.Player player) {
        PlayerAttributeDefaults.repairInvalidCameraDistance(player);
        PlayerInventory playerInventory = player.getInventory();
        inventory = cloneItems(playerInventory.getStorageContents());
        armor = cloneItems(playerInventory.getArmorContents());
        extra = cloneItems(playerInventory.getExtraContents());
        effects.clear();
        effects.addAll(player.getActivePotionEffects());
        attributeBases.clear();
        attributeModifiers.clear();
        for (Attribute attribute : attributeRegistry()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                attributeBases.put(attribute, PlayerAttributeDefaults.snapshotBaseValue(instance));
                attributeModifiers.put(attribute, new ArrayList<>(instance.getModifiers()));
            }
        }
        health = Math.max(0.0, player.getHealth());
        foodLevel = player.getFoodLevel();
        saturation = player.getSaturation();
        arrowsInBody = player.getArrowsInBody();
        fireTicks = player.getFireTicks();
        level = player.getLevel();
        exp = player.getExp();
        totalExperience = player.getTotalExperience();
        gameMode = player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode();
        invulnerable = player.isInvulnerable();
        invisible = player.isInvisible();
        canPickupItems = player.getCanPickupItems();
        allowFlight = player.getAllowFlight();
        flying = player.isFlying();
        touchTimedValues();
    }

    void apply(org.bukkit.entity.Player player) {
        ageTimedValues();
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.setStorageContents(cloneItems(inventory));
        playerInventory.setArmorContents(cloneItems(armor));
        playerInventory.setExtraContents(cloneItems(extra));

        player.clearActivePotionEffects();
        player.addPotionEffects(effects);
        applyAttributes(player);

        player.setGameMode(gameMode);
        player.setInvulnerable(invulnerable);
        player.setInvisible(invisible);
        player.setCanPickupItems(canPickupItems);
        boolean effectiveAllowFlight = allowsFlight();
        player.setAllowFlight(effectiveAllowFlight);
        player.setFlying(effectiveAllowFlight && flying);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setArrowsInBody(arrowsInBody);
        player.setFireTicks(fireTicks);
        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExperience);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(Math.max(1.0, health), maxHealth == null ? 20.0 : maxHealth.getValue()));
        touchTimedValues();
    }

    boolean allowsFlight() {
        return allowFlight || gameMode == GameMode.CREATIVE;
    }

    void reset(GameMode gameMode) {
        inventory = new ItemStack[36];
        armor = new ItemStack[4];
        extra = new ItemStack[1];
        effects.clear();
        attributeBases.clear();
        attributeModifiers.clear();
        health = 20.0;
        foodLevel = 20;
        saturation = 20.0f;
        arrowsInBody = 0;
        fireTicks = 0;
        level = 0;
        exp = 0.0f;
        totalExperience = 0;
        this.gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        invulnerable = false;
        invisible = false;
        canPickupItems = true;
        allowFlight = this.gameMode == GameMode.CREATIVE;
        flying = false;
        touchTimedValues();
    }

    void clearSpectatorProjection() {
        if (gameMode == GameMode.SPECTATOR) {
            gameMode = GameMode.SURVIVAL;
        }
        invulnerable = false;
        invisible = false;
        canPickupItems = true;
        allowFlight = gameMode == GameMode.CREATIVE;
        flying = false;
    }

    void ageTimedValues() {
        long now = System.currentTimeMillis();
        int elapsedTicks = (int) ((now - lastTimedUpdateMillis) / 50L);
        if (elapsedTicks <= 0) {
            return;
        }
        lastTimedUpdateMillis += elapsedTicks * 50L;
        fireTicks = Math.max(0, fireTicks - elapsedTicks);
        List<PotionEffect> agedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) {
            if (effect.isInfinite()) {
                agedEffects.add(effect);
                continue;
            }
            int remainingTicks = effect.getDuration() - elapsedTicks;
            if (remainingTicks > 0) {
                agedEffects.add(effect.withDuration(remainingTicks));
            }
        }
        effects.clear();
        effects.addAll(agedEffects);
    }

    void addItem(ItemStack item) {
        ItemStack remaining = item.clone();
        for (ItemStack current : inventory) {
            if (current == null || !current.isSimilar(remaining)) {
                continue;
            }
            int transfer = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
            if (transfer <= 0) {
                continue;
            }
            current.setAmount(current.getAmount() + transfer);
            remaining.setAmount(remaining.getAmount() - transfer);
            if (remaining.getAmount() <= 0) {
                return;
            }
        }
        for (int index = 0; index < inventory.length; index++) {
            if (inventory[index] != null) {
                continue;
            }
            int transfer = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            inventory[index] = remaining.clone();
            inventory[index].setAmount(transfer);
            remaining.setAmount(remaining.getAmount() - transfer);
            if (remaining.getAmount() <= 0) {
                return;
            }
        }
    }

    void touchTimedValues() {
        lastTimedUpdateMillis = System.currentTimeMillis();
    }

    void clearItems(Predicate<ItemStack> matcher) {
        clearItems(inventory, matcher);
        clearItems(armor, matcher);
        clearItems(extra, matcher);
    }

    void setItem(EquipmentSlot slot, ItemStack item) {
        ItemStack clone = item == null ? null : item.clone();
        switch (slot) {
            case HAND -> inventory[0] = clone;
            case OFF_HAND -> extra[0] = clone;
            case FEET -> armor[0] = clone;
            case LEGS -> armor[1] = clone;
            case CHEST -> armor[2] = clone;
            case HEAD -> armor[3] = clone;
            default -> {
            }
        }
    }

    ItemStack getItem(EquipmentSlot slot) {
        ItemStack item = switch (slot) {
            case HAND -> inventory[0];
            case OFF_HAND -> extra[0];
            case FEET -> armor[0];
            case LEGS -> armor[1];
            case CHEST -> armor[2];
            case HEAD -> armor[3];
            default -> null;
        };
        return item == null ? null : item.clone();
    }

    static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] clone = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            clone[index] = items[index] == null ? null : items[index].clone();
        }
        return clone;
    }

    private void applyAttributes(org.bukkit.entity.Player player) {
        for (Attribute attribute : attributeRegistry()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            PlayerAttributeDefaults.clearModifiers(instance);
            Double baseValue = attributeBases.get(attribute);
            if (baseValue == null) {
                PlayerAttributeDefaults.restoreVanillaBase(instance);
                continue;
            }
            if (PlayerAttributeDefaults.isInvalidCameraDistance(attribute, baseValue)) {
                PlayerAttributeDefaults.restoreVanillaBase(instance);
                continue;
            }
            instance.setBaseValue(baseValue);
            for (AttributeModifier modifier : attributeModifiers.getOrDefault(attribute, List.of())) {
                instance.addModifier(modifier);
            }
        }
    }

    private void clearItems(ItemStack[] items, Predicate<ItemStack> matcher) {
        for (int index = 0; index < items.length; index++) {
            if (matcher.test(items[index])) {
                items[index] = null;
            }
        }
    }

    private static Registry<Attribute> attributeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
    }
}
