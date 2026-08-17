package com.donutsforlife11.donutgame.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEntityEvent<T extends Event> extends GameEvent<T> {
    private final GameEntity entity;

    GameEntityEvent(T event, GameEntity entity) {
        this(event, entity, null);
    }

    GameEntityEvent(T event, GameEntity entity, GameModule module) {
        super(event, module);
        this.entity = entity;
    }

    public GameEntity getEntity() {
        return entity;
    }

    public GameEntity getDamager() {
        Object damager = GameEventRegistrar.invokeGetter(bukkitEvent(), "getDamager");
        return damager instanceof Entity entity ? gameEntity(entity) : null;
    }
}
