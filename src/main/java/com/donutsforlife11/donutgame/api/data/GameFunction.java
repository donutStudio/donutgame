package com.donutsforlife11.donutgame.api.data;

import org.bukkit.NamespacedKey;

/**
 * A read-only reference to a function owned by a module.
 */
public final class GameFunction {
    private final NamespacedKey key;
    private final GameDataResource source;

    GameFunction(NamespacedKey key, GameDataResource source) {
        this.key = key;
        this.source = source;
    }

    public NamespacedKey key() {
        return key;
    }

    public GameDataResource source() {
        return source;
    }
}
