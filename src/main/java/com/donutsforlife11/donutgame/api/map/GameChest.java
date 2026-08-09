package com.donutsforlife11.donutgame.api.map;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GameChest {
    private final GameWorld world;
    private final GameLocation location;

    GameChest(GameWorld world, GameLocation location) {
        this.world = world;
        this.location = location;
    }

    public void clear() {
        chest().getBlockInventory().clear();
    }

    public void addItems(Collection<ItemStack> items) {
        Inventory inventory = chest().getBlockInventory();
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                emptySlots.add(slot);
            }
        }
        Collections.shuffle(emptySlots, ThreadLocalRandom.current());
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (emptySlots.isEmpty()) {
                inventory.addItem(item);
                continue;
            }
            inventory.setItem(emptySlots.removeLast(), item);
        }
    }

    public GameLocation location() {
        return location;
    }

    public boolean exists() {
        return world.bukkitWorld().getBlockAt(location.toBukkit(world.bukkitWorld())).getType() == Material.CHEST;
    }

    public GameChest ensurePresent() {
        if (!exists()) {
            world.setBlock(location, Material.CHEST);
        }
        return this;
    }

    private Chest chest() {
        return (Chest) world.bukkitWorld().getBlockAt(location.toBukkit(world.bukkitWorld())).getState();
    }
}
