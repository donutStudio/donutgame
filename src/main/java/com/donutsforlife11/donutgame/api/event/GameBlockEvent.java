package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameBlockEvent<T extends Event> extends GameEvent<T> {
    private final GameLocation location;

    GameBlockEvent(T event, GameLocation location) {
        this(event, location, null);
    }

    GameBlockEvent(T event, GameLocation location, GameModule module) {
        super(event, module);
        this.location = location;
    }

    public GameLocation getLocation() {
        return location;
    }
}
