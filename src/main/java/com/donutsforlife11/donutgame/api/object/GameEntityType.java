package com.donutsforlife11.donutgame.api.object;

import java.util.Objects;

import org.bukkit.entity.EntityType;

public final class GameEntityType {
    private final EntityType type;

    private GameEntityType(EntityType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static GameEntityType of(EntityType type) {
        return new GameEntityType(type);
    }

    public EntityType entityType() {
        return type;
    }
}
