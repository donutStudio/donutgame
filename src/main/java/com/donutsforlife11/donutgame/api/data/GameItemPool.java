package com.donutsforlife11.donutgame.api.data;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.internal.item.GameItemService;

public class GameItemPool {
    private final GameItemService itemService;
    private final List<String> entries;

    GameItemPool(GameItemService itemService, List<String> entries) {
        this.itemService = itemService;
        this.entries = List.copyOf(entries);
    }

    public List<String> entries() {
        return entries;
    }

    public ItemStack randomItem() {
        return itemService.randomPool(entries);
    }

    public ItemStack item(int index) {
        return itemService.parseItem(entries.get(index));
    }

    public List<ItemStack> items() {
        return entries.stream().map(itemService::parseItem).toList();
    }
}
