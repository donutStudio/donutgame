package com.donutsforlife11.donutgame.internal.player;

import java.util.ArrayList;
import java.util.HashSet;

import org.bukkit.GameMode;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.WorldPlayerState.AttributeState;

public class PlayerEvents implements Listener {
    private final PlayerStateStore playerStateStore;

    public PlayerEvents(PlayerStateStore playerStateStore, WorldService worldService) {
        this.playerStateStore = playerStateStore;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorldName = event.getFrom().getName();
        String toWorldName = player.getWorld().getName();
        if (fromWorldName == toWorldName) {
            return;
        }
        WorldPlayerState oldWorldState = savePlayerState(player);
        playerStateStore.save(player.getUniqueId(), fromWorldName, oldWorldState);
        WorldPlayerState newWorldState = playerStateStore.load(player.getUniqueId(), toWorldName);
        if (newWorldState == null) {
            newWorldState = createDefaultState(player);
            playerStateStore.save(player.getUniqueId(), toWorldName, newWorldState);
        }
        loadPlayerState(player, newWorldState);
    }
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldId = player.getWorld().getName();

        playerStateStore.save(player.getUniqueId(), worldId, savePlayerState(player));
    }

    public WorldPlayerState savePlayerState(Player player) {
        WorldPlayerState state = new WorldPlayerState();

        PlayerInventory inv = player.getInventory();

        state.inventory = cloneContents(inv.getStorageContents());
        state.armor = cloneContents(inv.getArmorContents());

        ItemStack offhand = inv.getItemInOffHand();
        state.offhand = offhand == null ? null : offhand.clone();

        state.enderChest = cloneContents(player.getEnderChest().getContents());

        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                AttributeState savedState = state.newAttributeState(attribute, instance.getBaseValue(), instance.getModifiers());
                state.attributes.add(savedState);
            }
        }
        state.health = player.getHealth();
        state.foodLevel = player.getFoodLevel();
        state.saturation = player.getSaturation();
        state.exhaustion = player.getExhaustion();

        state.level = player.getLevel();
        state.exp = player.getExp();
        state.totalExperience = player.getTotalExperience();

        state.gameMode = player.getGameMode();
        state.potionEffects = new ArrayList<>(player.getActivePotionEffects());
        return state;
    }

    public void loadPlayerState(Player player, WorldPlayerState state) {
        PlayerInventory inv = player.getInventory();

        inv.clear();
        inv.setStorageContents(state.inventory);
        inv.setArmorContents(state.armor);
        inv.setItemInOffHand(state.offhand);

        player.getEnderChest().clear();

        if (state.enderChest != null) {
            player.getEnderChest().setContents(state.enderChest);
        }

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (state.potionEffects != null) {
            for (PotionEffect effect : state.potionEffects) {
                player.addPotionEffect(effect);
            }
        }

        if (state.attributes != null) {
            for (AttributeState attributeState : state.attributes) {
                AttributeInstance instance = player.getAttribute(attributeState.attribute());
                if (instance != null) {
                    instance.setBaseValue(attributeState.base());
                    for (AttributeModifier modifier : instance.getModifiers()) {
                        instance.removeModifier(modifier);
                    }
                    for (AttributeModifier modifier : attributeState.modifiers()) {
                        instance.addModifier(modifier);
                    }
                }
            }
        }

        player.setHealth(Math.min(state.health, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        player.setFoodLevel(state.foodLevel);
        player.setSaturation(state.saturation);
        player.setExhaustion(state.exhaustion);

        player.setLevel(state.level);
        player.setExp(state.exp);
        player.setTotalExperience(state.totalExperience);

        if (state.gameMode != null) {
            player.setGameMode(state.gameMode);
        }

        player.updateInventory();
    }

    private WorldPlayerState createDefaultState(Player player) {
        WorldPlayerState state = new WorldPlayerState();

        state.inventory = new ItemStack[36];
        state.armor = new ItemStack[4];
        state.offhand = null;
        state.enderChest = new ItemStack[27];

        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                state.attributes.add(new AttributeState(attribute, instance.getDefaultValue(), new HashSet<>()));
            }
        }
        state.foodLevel = 20;
        state.saturation = 5.0f;
        state.exhaustion = 0.0f;

        state.level = 0;
        state.exp = 0.0f;
        state.totalExperience = 0;

        state.gameMode = GameMode.ADVENTURE;
        state.potionEffects = new ArrayList<>();
        return state;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }

        return clone;
    }
}
