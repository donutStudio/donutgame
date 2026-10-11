package com.donutsforlife11.donutgame.api.item;

import org.bukkit.inventory.ItemStack;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should use {@link GameItem} or copy semantics instead.
 */
public final class GameItemHandle {
    private GameItemHandle() {
    }

    public static ItemStack bukkitItem(GameItem item) {
        return item == null ? null : item.bukkitItem();
    }
}
