package com.donutsforlife11.donutgame.api.event;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEventRegistrar {
    private final GameModule module;

    public GameEventRegistrar(GameModule module) {
        this.module = module;
    }

    public <T extends Event> void player(Class<T> eventType, Function<T, Player> playerGetter, Consumer<GamePlayerEvent<T>> handler) {
        register(eventType, event -> {
            Player player = playerGetter.apply(event);
            GamePlayer gamePlayer = module.playerManager().getPlayer(player);
            return gamePlayer == null ? null : new GamePlayerEvent<>(event, gamePlayer);
        }, handler);
    }

    public <T extends Event> void entity(Class<T> eventType, Function<T, Entity> entityGetter, Consumer<GameEntityEvent<T>> handler) {
        register(eventType, event -> {
            Entity entity = entityGetter.apply(event);
            if (entity == null || !module.world().contains(entity)) {
                return null;
            }
            GameEntity gameEntity = module.world().entity(entity);
            return gameEntity == null ? null : new GameEntityEvent<>(event, gameEntity);
        }, handler);
    }

    public <T extends Event> void location(Class<T> eventType, Function<T, Location> locationGetter, Consumer<GameLocationEvent<T>> handler) {
        register(eventType, event -> {
            Location location = locationGetter.apply(event);
            if (!module.world().contains(location)) {
                return null;
            }
            return new GameLocationEvent<>(event, GameLocation.fromBukkit(location));
        }, handler);
    }

    public <T extends Event> void block(Class<T> eventType, Function<T, Location> locationGetter, Consumer<GameBlockEvent<T>> handler) {
        register(eventType, event -> {
            Location location = locationGetter.apply(event);
            if (!module.world().contains(location)) {
                return null;
            }
            return new GameBlockEvent<>(event, GameLocation.fromBukkit(location));
        }, handler);
    }

    private <T extends Event, G> void register(Class<T> eventType, Function<T, G> wrapperFactory, Consumer<G> handler) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(wrapperFactory);
        Objects.requireNonNull(handler);
        Listener listener = new Listener() {
        };
        EventExecutor executor = (ignored, event) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            G wrapped = wrapperFactory.apply(eventType.cast(event));
            if (wrapped != null) {
                handler.accept(wrapped);
            }
        };
        module.registerDynamicEvent(listener, eventType, executor);
    }
}
