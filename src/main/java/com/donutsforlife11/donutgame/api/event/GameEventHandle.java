package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should prefer scoped event methods and properties.
 */
public final class GameEventHandle {
    private GameEventHandle() {
    }

    public static <E extends Event> E bukkitEvent(GameEvent<E> event) {
        return event == null ? null : event.bukkitEvent();
    }
}
