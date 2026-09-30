package com.donutsforlife11.donutgame.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public final class GameItemComponents {
    public static final GameItemComponent<String> DUMMY = string("dummy");
    public static final GameItemComponent<String> SPECTATOR_MENU = string("spectator_menu");

    private static final String NAMESPACE = "donutgame";

    private GameItemComponents() {
    }

    public static GameItemComponent<String> string(String key) {
        return of(key, PersistentDataType.STRING);
    }

    public static <T> GameItemComponent<T> of(String key, PersistentDataType<?, T> type) {
        return new GameItemComponent<>(new NamespacedKey(NAMESPACE, key), type);
    }

    public static <T> GameItemComponent<T> of(NamespacedKey key, PersistentDataType<?, T> type) {
        return new GameItemComponent<>(key, type);
    }
}
