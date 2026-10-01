package com.donutsforlife11.donutgame.api.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.WorldEvent;

final class GameEventReflection {
    private static final Map<AccessorKey, Method[]> GETTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<AccessorKey, Method[]> SETTER_CACHE = new ConcurrentHashMap<>();

    private GameEventReflection() {
    }

    static Object get(Object target, String property) {
        if (target == null || property == null || property.isBlank()) {
            return null;
        }

        Object directValue = directGet(target, property);
        if (directValue != null) {
            return directValue;
        }

        for (Method method : getters(target.getClass(), property)) {
            Object value = invoke(target, method);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static boolean set(Object target, String property, Object value) {
        if (target == null || property == null || property.isBlank()) {
            return false;
        }
        for (Method method : setters(target.getClass(), property)) {
            if (invokeSetter(method, target, value)) {
                return true;
            }
        }
        return false;
    }

    static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invokeSetter(Method method, Object target, Object value) {
        Class<?> parameterType = wrapPrimitive(method.getParameterTypes()[0]);
        if (value == null) {
            if (method.getParameterTypes()[0].isPrimitive()) {
                return false;
            }
        } else if (!parameterType.isInstance(value)) {
            return false;
        }
        try {
            method.invoke(target, value);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return false;
        }
    }

    private static Method[] getters(Class<?> type, String property) {
        return GETTER_CACHE.computeIfAbsent(new AccessorKey(type, property), key -> Arrays.stream(getterNames(key.property()))
            .map(name -> getter(key.type(), name))
            .filter(method -> method != null)
            .toArray(Method[]::new));
    }

    private static Method getter(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            return method.getParameterCount() == 0 ? method : null;
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method[] setters(Class<?> type, String property) {
        return SETTER_CACHE.computeIfAbsent(new AccessorKey(type, property), key -> {
            String setterName = "set" + capitalize(key.property());
            return Arrays.stream(key.type().getMethods())
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getName().equals(setterName) || method.getName().equals(key.property()))
                .toArray(Method[]::new);
        });
    }

    private static String[] getterNames(String property) {
        String suffix = capitalize(property);
        return new String[] { property, "get" + suffix, "is" + suffix };
    }

    private static Object directGet(Object target, String property) {
        return switch (property) {
            case "player" -> target instanceof PlayerEvent event ? event.getPlayer() : null;
            case "from" -> target instanceof PlayerMoveEvent event ? event.getFrom() : null;
            case "to" -> target instanceof PlayerMoveEvent event ? event.getTo() : null;
            case "block" -> target instanceof BlockEvent event ? event.getBlock() : null;
            case "clickedBlock" -> target instanceof PlayerInteractEvent event ? event.getClickedBlock() : null;
            case "entity" -> target instanceof EntityEvent event ? event.getEntity() : null;
            case "damager" -> target instanceof EntityDamageByEntityEvent event ? event.getDamager() : null;
            case "world" -> target instanceof WorldEvent event ? event.getWorld() : null;
            default -> null;
        };
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

    private record AccessorKey(Class<?> type, String property) {
    }
}
