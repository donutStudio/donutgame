package com.donutsforlife11.donutgame.api.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
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

    public void registerAnnotated(Object target) {
        Objects.requireNonNull(target);
        for (Class<?> current = target.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                GameEventHandler annotation = method.getAnnotation(GameEventHandler.class);
                if (annotation != null) {
                    registerAnnotated(target, method, annotation);
                }
            }
        }
    }

    private void registerAnnotated(Object target, Method method, GameEventHandler annotation) {
        if (method.isSynthetic() || method.getParameterCount() != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must have exactly one parameter.");
        }
        Class<?> wrapperType = method.getParameterTypes()[0];
        if (!GameEvent.class.isAssignableFrom(wrapperType)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must accept a GameEvent wrapper.");
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
            GameEvent<?> wrapped = wrap(eventType.cast(event), wrapperType);
            if (wrapped == null) {
                return;
            }
            try {
                method.invoke(target, wrapped);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to call game event handler " + method.getName() + ".", exception);
            } catch (InvocationTargetException exception) {
                module.logError("Game event handler " + method.getName() + " failed.", exception.getTargetException());
            }
        };
        module.registerDynamicEvent(listener, eventType, executor, annotation.priority(), annotation.ignoreCancelled());
    }

    private Class<? extends Event> eventType(Method method) {
        Type parameter = method.getGenericParameterTypes()[0];
        if (!(parameter instanceof ParameterizedType parameterized) || parameterized.getActualTypeArguments().length != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must use a typed wrapper like GamePlayerEvent<PlayerDeathEvent>.");
        }
        Type eventType = parameterized.getActualTypeArguments()[0];
        if (!(eventType instanceof Class<?> eventClass) || !Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must wrap a Bukkit Event type.");
        }
        return eventClass.asSubclass(Event.class);
    }

    private GameEvent<?> wrap(Event event, Class<?> wrapperType) {
        if (event instanceof PlayerMoveEvent moveEvent && !isScopedMove(moveEvent)) {
            return null;
        }
        if (GamePlayerEvent.class.isAssignableFrom(wrapperType)) {
            GamePlayer player = toGamePlayer(findPlayer(event));
            return player == null ? null : new GamePlayerEvent<>(event, player, module);
        }
        if (GameEntityEvent.class.isAssignableFrom(wrapperType)) {
            GameEntity entity = toGameEntity(findEntity(event));
            return entity == null ? null : new GameEntityEvent<>(event, entity, module);
        }
        if (GameBlockEvent.class.isAssignableFrom(wrapperType)) {
            GameLocation location = toGameLocation(findBlockLocation(event));
            return location == null ? null : new GameBlockEvent<>(event, location, module);
        }
        if (GameLocationEvent.class.isAssignableFrom(wrapperType)) {
            GameLocation location = toGameLocation(findLocation(event));
            return location == null ? null : new GameLocationEvent<>(event, location, module);
        }
        if (GameEvent.class.equals(wrapperType)) {
            return isInScope(event) ? new GameEvent<>(event, module) : null;
        }
        throw new IllegalStateException("Unsupported game event wrapper " + wrapperType.getName() + ".");
    }

    private boolean isScopedMove(PlayerMoveEvent event) {
        return toGamePlayer(event.getPlayer()) != null
            && toGameLocation(event.getFrom()) != null
            && toGameLocation(event.getTo()) != null;
    }

    private boolean isInScope(Event event) {
        return toGamePlayer(findPlayer(event)) != null
            || toGameEntity(findEntity(event)) != null
            || toGameLocation(findLocation(event)) != null
            || toGameLocation(findBlockLocation(event)) != null;
    }

    private GamePlayer toGamePlayer(Player player) {
        return player == null ? null : module.playerManager().getPlayer(player);
    }

    private GameEntity toGameEntity(Entity entity) {
        if (entity == null || !module.world().contains(entity)) {
            return null;
        }
        return module.world().entity(entity);
    }

    private GameLocation toGameLocation(Location location) {
        return module.world().contains(location) ? GameLocation.fromBukkit(location) : null;
    }

    private Player findPlayer(Event event) {
        Object player = invokeGetter(event, "getPlayer");
        if (player instanceof Player bukkitPlayer) {
            return bukkitPlayer;
        }
        Object entity = invokeGetter(event, "getEntity");
        return entity instanceof Player bukkitPlayer ? bukkitPlayer : null;
    }

    private Entity findEntity(Event event) {
        Object entity = invokeGetter(event, "getEntity");
        if (entity instanceof Entity bukkitEntity) {
            return bukkitEntity;
        }
        Object player = invokeGetter(event, "getPlayer");
        return player instanceof Entity bukkitEntity ? bukkitEntity : null;
    }

    private Location findLocation(Event event) {
        Object location = invokeGetter(event, "getLocation");
        if (location instanceof Location bukkitLocation) {
            return bukkitLocation;
        }
        Location blockLocation = findBlockLocation(event);
        if (blockLocation != null) {
            return blockLocation;
        }
        Entity entity = findEntity(event);
        return entity == null ? null : entity.getLocation();
    }

    private Location findBlockLocation(Event event) {
        Object block = invokeGetter(event, "getBlock");
        Block bukkitBlock = block instanceof Block directBlock ? directBlock : null;
        if (bukkitBlock == null) {
            block = invokeGetter(event, "getClickedBlock");
            if (!(block instanceof Block clickedBlock)) {
                return null;
            }
            bukkitBlock = clickedBlock;
        }
        return bukkitBlock.getLocation();
    }

    public static Object invokeGetter(Object target, String name) {
        return invokeGetter(target, new String[] { name });
    }

    public static Object invokeGetter(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object value = invokeSingleGetter(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invokeSingleGetter(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (IllegalAccessException exception) {
            return null;
        } catch (InvocationTargetException exception) {
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static boolean invokeSetter(Object target, String name, Object value) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (value == null && parameterType.isPrimitive()) {
                continue;
            }
            if (value != null && !wrapPrimitive(parameterType).isInstance(value)) {
                continue;
            }
            try {
                method.invoke(target, value);
                return true;
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }
}
