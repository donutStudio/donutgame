package com.donutsforlife11.donutgame.api.item;

import org.bukkit.loot.LootTable;
import com.donutsforlife11.donutgame.api.map.GameWorld;

/** Framework bridge for installing a scoped loot table on a container. */
public final class GameLootTableHandle {
    private GameLootTableHandle() {}

    public static LootTable resolve(GameLootTable table, GameWorld world) {
        return table == null ? null : table.resolve(world);
    }
}
