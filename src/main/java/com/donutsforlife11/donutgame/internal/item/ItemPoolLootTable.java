package com.donutsforlife11.donutgame.internal.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

class ItemPoolLootTable implements LootTable {
    private final NamespacedKey key;
    private final List<ItemStack> entries;

    ItemPoolLootTable(List<ItemStack> entries) {
        this.key = new NamespacedKey("donutgame", "pool/" + Integer.toUnsignedString(entries.hashCode(), 36));
        this.entries = entries.stream()
            .filter(Objects::nonNull)
            .filter(item -> item.getType() != Material.AIR)
            .map(item -> item.clone())
            .toList();
    }

    @Override
    public Collection<ItemStack> populateLoot(Random random, LootContext context) {
        List<ItemStack> loot = new ArrayList<>(1);
        if (entries.isEmpty()) {
            return loot;
        }
        Random source = random == null ? ThreadLocalRandom.current() : random;
        loot.add(entries.get(source.nextInt(entries.size())).clone());
        return loot;
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
