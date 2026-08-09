package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.map.GameLocation;

public record GameLocationEvent<T extends Event>(
    T event,
    GameLocation location
) {
}
