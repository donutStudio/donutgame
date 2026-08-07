package com.donutsforlife11.donutgame.api.event;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEventRegistrar {
    private final GameModule module;

    public GameEventRegistrar(GameModule module) {
        this.module = module;
    }

    public <T extends PlayerEvent> void onPlayerEvent(Class<T> eventType, Consumer<T> handler) {
        register(eventType, event -> module.playerManager().isRegistered(event.getPlayer()), handler);
    }

    public <T extends EntityEvent> void onEntityEvent(Class<T> eventType, Consumer<T> handler) {
        register(eventType, event -> module.world().contains(event.getEntity()), handler);
    }

    public <T extends BlockEvent> void onBlockEvent(Class<T> eventType, Consumer<T> handler) {
        register(eventType, event -> module.world().contains(event.getBlock().getLocation()), handler);
    }

    private <T extends Event> void register(Class<T> eventType, GameEventFilter<T> filter, Consumer<T> handler) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(filter);
        Objects.requireNonNull(handler);
        Listener listener = new Listener() {
        };
        EventExecutor executor = (ignored, event) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            T typedEvent = eventType.cast(event);
            if (filter.allows(typedEvent)) {
                handler.accept(typedEvent);
            }
        };
        module.registerDynamicEvent(listener, eventType, executor);
    }

    @FunctionalInterface
    private interface GameEventFilter<T extends Event> {
        boolean allows(T event);
    }
}
