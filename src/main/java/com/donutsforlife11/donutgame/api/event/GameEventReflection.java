package com.donutsforlife11.donutgame.api.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class GameEventReflection {
    private GameEventReflection() {
    }

    static Object get(Object target, String property) {
        if (target == null || property == null || property.isBlank()) {
            return null;
        }
        for (String name : getterNames(property)) {
            Object value = invoke(target, name);
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
        return invokeSetter(target, "set" + capitalize(property), value) || invokeSetter(target, property, value);
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

    private static boolean invokeSetter(Object target, String methodName, Object value) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = wrapPrimitive(method.getParameterTypes()[0]);
            if (value == null) {
                if (method.getParameterTypes()[0].isPrimitive()) {
                    continue;
                }
            } else if (!parameterType.isInstance(value)) {
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

    private static String[] getterNames(String property) {
        String suffix = capitalize(property);
        return new String[] { property, "get" + suffix, "is" + suffix };
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
}
