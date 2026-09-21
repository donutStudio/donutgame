package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEvent<E extends Event> implements GameEventProperties<E> {
    private final E event;
    private final GameModule module;
    private final GameEventAdapterRegistry adapterRegistry;

    public GameEvent(E event, GameModule module, GameEventAdapterRegistry adapterRegistry) {
        this.event = event;
        this.module = module;
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public E event() {
        return event;
    }

    public E bukkitEvent() {
        return event;
    }

    @Override
    public GameModule module() {
        return module;
    }

    public Class<? extends Event> eventType() {
        return event.getClass();
    }

    public GameEventAdapterRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    @Override
    public Object getRaw(String property) {
        return GameEventReflection.get(event, property);
    }

    @Override
    public <T> T get(String property, Class<T> type) {
        return adapterRegistry.adapt(getRaw(property), type, module);
    }

    @Override
    public void set(String property, Object value) {
        GameEventReflection.set(event, property, unwrap(value));
    }

    private Object unwrap(Object value) {
        if (value instanceof com.donutsforlife11.donutgame.api.player.GamePlayer player) {
            return player.bukkitPlayer();
        }
        if (value instanceof com.donutsforlife11.donutgame.api.entity.GameEntity entity) {
            return entity.bukkitEntity();
        }
        if (value instanceof com.donutsforlife11.donutgame.api.map.GameLocation location) {
            return location.bukkitLocation();
        }
        if (value instanceof com.donutsforlife11.donutgame.api.map.GameWorld world) {
            return world.bukkitWorld();
        }
        if (value instanceof com.donutsforlife11.donutgame.api.item.GameItem item) {
            return item.bukkitItem();
        }
        return value;
    }
}
