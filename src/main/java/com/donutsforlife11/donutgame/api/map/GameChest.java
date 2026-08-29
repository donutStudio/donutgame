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
import org.bukkit.loot.LootTable;

import com.donutsforlife11.donutgame.api.data.GameItems;

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

    public void setLootTable(LootTable lootTable) {
        setLootTable(lootTable, ThreadLocalRandom.current().nextLong());
    }

    public void setLootTable(LootTable lootTable, long seed) {
        setItems(GameItems.items(lootTable, seed));
    }

    public void addLootTable(LootTable lootTable) {
        addLootTable(lootTable, ThreadLocalRandom.current().nextLong());
    }

    public void addLootTable(LootTable lootTable, long seed) {
        addItems(GameItems.items(lootTable, seed));
    }

    public void setItems(Collection<ItemStack> items) {
        clear();
        addItems(items);
    }

    public void addItems(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
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
                inventory.addItem(item.clone());
                continue;
            }
            inventory.setItem(emptySlots.removeLast(), item.clone());
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
