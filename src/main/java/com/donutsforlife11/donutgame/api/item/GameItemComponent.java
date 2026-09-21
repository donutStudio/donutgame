package com.donutsforlife11.donutgame.api.item;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public record GameItemComponent<T>(NamespacedKey key, PersistentDataType<?, T> type) {
    public GameItemComponent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
    }
}
