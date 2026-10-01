package com.donutsforlife11.donutgame.api.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEventRegistrar {
    private final GameModule module;
    private final GameEventAdapterRegistry adapterRegistry;
    private final Set<HandlerKey> registeredHandlers = new HashSet<>();

    public GameEventRegistrar(GameModule module) {
        this(module, GameEventAdapterRegistry.defaults());
    }

    public GameEventRegistrar(GameModule module, GameEventAdapterRegistry adapterRegistry) {
        this.module = Objects.requireNonNull(module, "module");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
    }

    public GameEventAdapterRegistry adapterRegistry() {
        return adapterRegistry;
    }

    public void registerAnnotated(Object target) {
        Objects.requireNonNull(target, "target");
        for (Class<?> current = target.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                GameEventHandler annotation = method.getAnnotation(GameEventHandler.class);
                if (annotation != null) {
                    register(target, method, annotation);
                }
            }
        }
    }

    private void register(Object target, Method method, GameEventHandler annotation) {
        if (!registeredHandlers.add(new HandlerKey(target, method))) {
            return;
        }
        if (method.isSynthetic() || method.getParameterCount() != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must have exactly one parameter.");
        }
        Class<?> parameterType = method.getParameterTypes()[0];
        if (!GameEvent.class.equals(parameterType)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must accept GameEvent<E>.");
        }
        Class<? extends Event> eventType = eventType(method);
        method.setAccessible(true);

        Listener listener = new Listener() {
        };
        EventExecutor executor = (ignored, event) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            if (annotation.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                return;
            }
            if (!isInScope(event)) {
                return;
            }
            try {
                method.invoke(target, new GameEvent<>(eventType.cast(event), module, adapterRegistry));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to call game event handler " + method.getName() + ".", exception);
            } catch (InvocationTargetException exception) {
                module.logError("Game event handler " + method.getName() + " failed.", exception.getTargetException());
            }
        };
        module.registerDynamicEvent(listener, eventType, executor, annotation.priority(), annotation.ignoreCancelled());
    }

    private Class<? extends Event> eventType(Method method) {
        Type type = method.getGenericParameterTypes()[0];
        if (!(type instanceof ParameterizedType parameterized) || parameterized.getActualTypeArguments().length != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must use GameEvent<PaperEventType>.");
        }
        Type eventType = parameterized.getActualTypeArguments()[0];
        if (!(eventType instanceof Class<?> eventClass) || !Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must wrap a Paper Event type.");
        }
        return eventClass.asSubclass(Event.class);
    }

    private boolean isInScope(Event event) {
        Boolean fastResult = fastScope(event);
        if (fastResult != null) {
            return fastResult;
        }

        Player player = player(event);
        if (player != null && (module.playerManager().owns(player) || module.playerManager().ownsOffline(player.getUniqueId()))) {
            return true;
        }
        Entity entity = entity(event);
        if (entity != null && world(entity.getWorld())) {
            return true;
        }
        Location location = location(event);
        if (location != null && world(location.getWorld())) {
            return true;
        }
        World world = raw(event, "world", World.class);
        return world(world);
    }

    private Boolean fastScope(Event event) {
        if (event instanceof PlayerEvent playerEvent) {
            Player player = playerEvent.getPlayer();
            return module.playerManager().owns(player) || module.playerManager().ownsOffline(player.getUniqueId());
        }
        if (event instanceof EntityEvent entityEvent) {
            Entity entity = entityEvent.getEntity();
            if (entity instanceof Player player) {
                return module.playerManager().owns(player) || module.playerManager().ownsOffline(player.getUniqueId());
            }
            return world(entity.getWorld());
        }
        if (event instanceof BlockEvent blockEvent) {
            return world(blockEvent.getBlock().getWorld());
        }
        if (event instanceof WorldEvent worldEvent) {
            return world(worldEvent.getWorld());
        }
        return null;
    }

    private Player player(Event event) {
        Player player = raw(event, "player", Player.class);
        if (player != null) {
            return player;
        }
        Entity entity = raw(event, "entity", Entity.class);
        return entity instanceof Player bukkitPlayer ? bukkitPlayer : null;
    }

    private Entity entity(Event event) {
        Entity entity = raw(event, "entity", Entity.class);
        if (entity != null) {
            return entity;
        }
        return raw(event, "player", Entity.class);
    }

    private Location location(Event event) {
        Location location = raw(event, "location", Location.class);
        if (location != null) {
            return location;
        }
        Block block = raw(event, "block", Block.class);
        if (block != null) {
            return block.getLocation();
        }
        block = raw(event, "clickedBlock", Block.class);
        if (block != null) {
            return block.getLocation();
        }
        Entity entity = entity(event);
        return entity == null ? null : entity.getLocation();
    }

    private boolean world(World world) {
        return adapterRegistry.adapt(world, com.donutsforlife11.donutgame.api.map.GameWorld.class, module) != null;
    }

    private <T> T raw(Event event, String property, Class<T> type) {
        Object value = GameEventReflection.get(event, property);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private record HandlerKey(Object target, Method method) {
        @Override
        public boolean equals(Object object) {
            return object instanceof HandlerKey other
                && target == other.target
                && method.equals(other.method);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(target) + method.hashCode();
        }
    }
}
