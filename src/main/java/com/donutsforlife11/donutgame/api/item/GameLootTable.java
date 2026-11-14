package com.donutsforlife11.donutgame.api.item;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public final class GameLootTable {
    private final GameModule owner;
    private final LootTable table;

    public GameLootTable(GameModule owner, LootTable table) {
        this.owner = Objects.requireNonNull(owner);
        this.table = Objects.requireNonNull(table);
    }

    public List<GameItem> roll() {
        return roll(ThreadLocalRandom.current().nextLong());
    }

    public List<GameItem> roll(long seed) {
        var location = GameLocationHandle.bukkitLocation(owner.world().getSpawnLocation());
        return table.populateLoot(new Random(seed), new LootContext.Builder(location).build())
            .stream().map(GameItem::from).toList();
    }

    LootTable resolve(GameWorld world) {
        if (owner.world() != world) {
            throw new IllegalArgumentException("Loot table belongs to a different game module.");
        }
        return table;
    }
}
