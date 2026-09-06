package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public interface GameEventAccess<E extends Event> {
    E event();

    GameModule module();

    Object getRaw(String property);

    <T> T get(String property, Class<T> type);

    void set(String property, Object value);
}
