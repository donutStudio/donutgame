package com.donutsforlife11.donutgame.internal.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.WorldPlayerState.AttributeState;

public class PlayerEvents implements Listener {
    private static final Map<Attribute, Double> VANILLA_PLAYER_ATTRIBUTE_BASES = Map.ofEntries(
        Map.entry(Attribute.MAX_HEALTH, 20.0),
        Map.entry(Attribute.FOLLOW_RANGE, 32.0),
        Map.entry(Attribute.KNOCKBACK_RESISTANCE, 0.0),
        Map.entry(Attribute.MOVEMENT_SPEED, 0.1),
        Map.entry(Attribute.FLYING_SPEED, 0.4),
        Map.entry(Attribute.ATTACK_DAMAGE, 1.0),
        Map.entry(Attribute.ATTACK_KNOCKBACK, 0.0),
        Map.entry(Attribute.ATTACK_SPEED, 4.0),
        Map.entry(Attribute.ARMOR, 0.0),
        Map.entry(Attribute.ARMOR_TOUGHNESS, 0.0),
        Map.entry(Attribute.LUCK, 0.0),
        Map.entry(Attribute.JUMP_STRENGTH, 0.42),
        Map.entry(Attribute.OXYGEN_BONUS, 0.0),
        Map.entry(Attribute.BURNING_TIME, 1.0),
        Map.entry(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0),
        Map.entry(Attribute.MOVEMENT_EFFICIENCY, 0.0),
        Map.entry(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.0),
        Map.entry(Attribute.BLOCK_BREAK_SPEED, 1.0),
        Map.entry(Attribute.SUBMERGED_MINING_SPEED, 0.2),
        Map.entry(Attribute.ENTITY_INTERACTION_RANGE, 3.0),
        Map.entry(Attribute.BLOCK_INTERACTION_RANGE, 4.5),
        Map.entry(Attribute.SAFE_FALL_DISTANCE, 3.0),
        Map.entry(Attribute.FALL_DAMAGE_MULTIPLIER, 1.0),
        Map.entry(Attribute.SNEAKING_SPEED, 0.3),
        Map.entry(Attribute.MINING_EFFICIENCY, 0.0),
        Map.entry(Attribute.SWEEPING_DAMAGE_RATIO, 0.0),
        Map.entry(Attribute.SCALE, 1.0),
        Map.entry(Attribute.STEP_HEIGHT, 0.6),
        Map.entry(Attribute.GRAVITY, 0.08),
        Map.entry(Attribute.CAMERA_DISTANCE, 4.0)
    );

    private final PlayerStateStore playerStateStore;
    private WorldService worldService;

    public PlayerEvents(PlayerStateStore playerStateStore) {
        this.playerStateStore = playerStateStore;
    }

    public void setWorldService(WorldService worldService) {
        this.worldService = worldService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorldName = worldService.getPlayerStateId(event.getFrom());
        String toWorldName = worldService.getPlayerStateId(player.getWorld());
        if (fromWorldName.equals(toWorldName)) {
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
        String worldId = worldService.getPlayerStateId(player.getWorld());

        playerStateStore.save(player.getUniqueId(), worldId, savePlayerState(player));
    }

    public WorldPlayerState savePlayerState(Player player) {
        WorldPlayerState state = new WorldPlayerState();

        PlayerInventory inv = player.getInventory();

        state.inventory = GamePlayer.stripSpectatorCompass(cloneContents(inv.getStorageContents()));
        state.armor = GamePlayer.stripSpectatorCompass(cloneContents(inv.getArmorContents()));

        ItemStack offhand = inv.getItemInOffHand();
        state.offhand = GamePlayer.stripSpectatorCompass(offhand == null ? null : offhand.clone());

        state.enderChest = cloneContents(player.getEnderChest().getContents());

        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                state.attributes.add(new AttributeState(attribute, instance.getBaseValue(), List.copyOf(instance.getModifiers())));
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
        inv.setStorageContents(GamePlayer.stripSpectatorCompass(cloneContents(state.inventory)));
        inv.setArmorContents(GamePlayer.stripSpectatorCompass(cloneContents(state.armor)));
        inv.setItemInOffHand(GamePlayer.stripSpectatorCompass(state.offhand));

        player.getEnderChest().clear();

        if (state.enderChest != null) player.getEnderChest().setContents(cloneContents(state.enderChest));

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

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(state.health, maxHealth == null ? state.health : maxHealth.getValue()));
        player.setFoodLevel(state.foodLevel);
        player.setSaturation(state.saturation);
        player.setExhaustion(state.exhaustion);

        player.setLevel(state.level);
        player.setExp(state.exp);
        player.setTotalExperience(state.totalExperience);

        if (state.gameMode != null) player.setGameMode(state.gameMode);

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
                state.attributes.add(new AttributeState(attribute, defaultBaseValue(attribute, instance), List.of()));
            }
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        state.health = maxHealth == null ? 20.0 : defaultBaseValue(Attribute.MAX_HEALTH, maxHealth);
        state.foodLevel = 20;
        state.saturation = 5.0f;
        state.exhaustion = 0.0f;

        state.level = 0;
        state.exp = 0.0f;
        state.totalExperience = 0;

        state.gameMode = GameMode.SURVIVAL;
        state.potionEffects = new ArrayList<>();
        return state;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) return null;
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone();
        return clone;
    }

    private double defaultBaseValue(Attribute attribute, AttributeInstance instance) {
        return VANILLA_PLAYER_ATTRIBUTE_BASES.getOrDefault(attribute, instance.getDefaultValue());
    }
}
