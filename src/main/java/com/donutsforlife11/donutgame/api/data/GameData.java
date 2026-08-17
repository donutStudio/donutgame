package com.donutsforlife11.donutgame.api.data;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.item.GameItemService;

public class GameData {
    private final GameModule module;
    private final GameItemService itemService;

    public GameData(GameModule module, GameItemService itemService) {
        this.module = module;
        this.itemService = itemService;
    }

    public LootTable lootTable(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(normalizeKey(key));
        if (namespacedKey == null) {
            throw new IllegalArgumentException("Invalid loot table key: " + key);
        }
        LootTable lootTable = Bukkit.getLootTable(namespacedKey);
        if (lootTable == null) {
            lootTable = itemService.lootTable(module, namespacedKey);
        }
        return lootTable;
    }

    public LootTable lootTable(List<ItemStack> items) {
        return itemService.lootTable(items);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Loot table key cannot be blank.");
        }
        return key.contains(":") ? key : module.id() + ":" + key;
    }
}
