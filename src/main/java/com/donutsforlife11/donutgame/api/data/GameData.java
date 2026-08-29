package com.donutsforlife11.donutgame.api.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameData {
    private final GameModule module;

    public GameData(GameModule module) {
        this.module = module;
    }

    public LootTable lootTable(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(normalizeKey(key));
        if (namespacedKey == null) {
            throw new IllegalArgumentException("Invalid loot table key: " + key);
        }
        LootTable bukkitTable = org.bukkit.Bukkit.getLootTable(namespacedKey);
        if (bukkitTable != null) {
            return bukkitTable;
        }
        throw new IllegalArgumentException("Unknown loot table: " + namespacedKey + ". Ensure the module jar is enabled as a datapack and run /minecraft:reload after updating module datapack data.");
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Loot table key cannot be blank.");
        }
        return key.contains(":") ? key : module.id() + ":" + key;
    }

    public LootTable lootTable(List<ItemStack> items) {
        return new ListLootTable(new NamespacedKey("donutgame", "inline/" + Integer.toUnsignedString(items.hashCode(), 36)), items);
    }

    private static final class ListLootTable implements LootTable {
        private final NamespacedKey key;
        private final List<ItemStack> items;

        private ListLootTable(NamespacedKey key, List<ItemStack> items) {
            this.key = key;
            this.items = items.stream()
                .filter(item -> item != null && item.getType() != Material.AIR)
                .map(ItemStack::clone)
                .toList();
        }

        @Override
        public Collection<ItemStack> populateLoot(Random random, LootContext context) {
            if (items.isEmpty()) return List.of();
            Random source = random == null ? ThreadLocalRandom.current() : random;
            return List.of(items.get(source.nextInt(items.size())).clone());
        }

        @Override
        public void fillInventory(Inventory inventory, Random random, LootContext context) {
            for (ItemStack item : populateLoot(random, context)) {
                inventory.addItem(item);
            }
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }
}
