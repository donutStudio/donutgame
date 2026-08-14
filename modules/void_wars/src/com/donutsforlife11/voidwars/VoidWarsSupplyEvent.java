package com.donutsforlife11.voidwars;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.map.GameLocation;

record VoidWarsSupplyEvent(int time, List<String> pool, String lootTable) {
    static VoidWarsSupplyEvent read(Map<?, ?> entry) {
        return new VoidWarsSupplyEvent(number(entry.get("time")), strings(entry.get("pool")), string(entry.get("loot_table")));
    }

    Collection<ItemStack> sharedItems(VoidWars game) {
        return pool.isEmpty() ? items(game.spawn(), game) : List.of(game.data().itemPool(pool).randomItem());
    }

    Collection<ItemStack> items(GameLocation location, VoidWars game) {
        if (!pool.isEmpty()) return List.of(game.data().itemPool(pool).randomItem());
        return lootTable == null ? List.of() : game.plugin().itemService().loot(game, lootTable, location);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(entry -> entry != null).map(String::valueOf).toList();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
