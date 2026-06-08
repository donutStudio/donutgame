package com.donutsforlife11.donutgame.util;

import java.util.ArrayList;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import com.donutsforlife11.donutgame.player.PlayerStateStore;
import com.donutsforlife11.donutgame.player.WorldPlayerState;

public class PlayerEvents implements Listener {
    private final PlayerStateStore stateStore;
    private final WorldManager worldManager;

    public PlayerEvents(PlayerStateStore stateStore, WorldManager worldManager) {
        this.stateStore = stateStore;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        String fromWorldName = getWorldStateId(event.getFrom());
        String toWorldName = getWorldStateId(player.getWorld());

        if (fromWorldName.equals(toWorldName)) {
            return;
        }

        WorldPlayerState oldWorldState = savePlayerState(player);
        stateStore.save(player.getUniqueId(), fromWorldName, oldWorldState);

        WorldPlayerState newWorldState = stateStore.load(player.getUniqueId(), toWorldName);
        if (newWorldState == null) {
            newWorldState = createDefaultState(player);
            stateStore.save(player.getUniqueId(), toWorldName, newWorldState);
        }

        loadPlayerState(player, newWorldState);
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldId = getWorldStateId(player.getWorld());

        stateStore.save(player.getUniqueId(), worldId, savePlayerState(player));
    }

    private String getWorldStateId(World world) {
        return worldManager.getPlayerStateId(world);
    }

    public WorldPlayerState savePlayerState(Player player) {
        WorldPlayerState state = new WorldPlayerState();

        PlayerInventory inv = player.getInventory();

        state.inventory = cloneContents(inv.getStorageContents());
        state.armor = cloneContents(inv.getArmorContents());

        ItemStack offhand = inv.getItemInOffHand();
        state.offhand = offhand == null ? null : offhand.clone();

        state.enderChest = cloneContents(player.getEnderChest().getContents());

        state.health = Math.min(player.getHealth(), getMaxHealth(player));
        state.foodLevel = player.getFoodLevel();
        state.saturation = player.getSaturation();
        state.exhaustion = player.getExhaustion();

        state.level = player.getLevel();
        state.exp = player.getExp();
        state.totalExperience = player.getTotalExperience();

        state.gameMode = player.getGameMode();
        state.potionEffects = new ArrayList<>(player.getActivePotionEffects());

        state.lastLocation = player.getLocation().clone();

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

        double maxHealth = getMaxHealth(player);
        player.setHealth(Math.max(0.1, Math.min(state.health, maxHealth)));

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

        state.health = getMaxHealth(player);
        state.foodLevel = 20;
        state.saturation = 5.0f;
        state.exhaustion = 0.0f;

        state.level = 0;
        state.exp = 0.0f;
        state.totalExperience = 0;

        state.gameMode = GameMode.ADVENTURE;
        state.potionEffects = new ArrayList<>();

        state.lastLocation = player.getLocation().clone();

        return state;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }

        return clone;
    }

    private double getMaxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);

        if (attribute == null) {
            return 20.0;
        }

        return attribute.getValue();
    }
}
