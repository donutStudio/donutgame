package com.donutsforlife11.donutgame.api.event;

import java.util.List;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.internal.game.GameModule;

abstract class GameEventAccess<E extends Event> {
    abstract GameModule module();

    abstract Object getRaw(String property);

    abstract <T> T get(String property, Class<T> type);

    abstract <T> List<T> getList(String property, Class<T> type);

    abstract void set(String property, Object value);
}
