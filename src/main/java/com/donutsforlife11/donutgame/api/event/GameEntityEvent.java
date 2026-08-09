package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.entity.GameEntity;

public record GameEntityEvent<T extends Event>(
    T event,
    GameEntity entity
) {
}
