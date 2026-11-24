package com.donutsforlife11.donutgame.api.item;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemNotationChecks {
    public static void main(String[] args) throws Exception {
        var field = ItemNotation.class.getDeclaredField("CUSTOM_DATA_ENTRY_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        var setter = ItemNotation.class.getDeclaredMethod("setPersistentValue", PersistentDataContainer.class, NamespacedKey.class, String.class);
        setter.setAccessible(true);
        Map<String, Object[]> values = new HashMap<>();
        var container = (PersistentDataContainer) Proxy.newProxyInstance(ItemNotationChecks.class.getClassLoader(),
            new Class<?>[]{PersistentDataContainer.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("set")) values.put(arguments[0].toString(), new Object[]{arguments[1], arguments[2]});
                return null;
            });
        var matcher = pattern.matcher("\"donutgame:infinite_build\":1b, \"donutgame:team_sync\":1B, donutgame:count:25, donutgame:enabled:true, donutgame:large:42L");
        while (matcher.find()) setter.invoke(null, container, NamespacedKey.fromString(matcher.group(1)), matcher.group(2));
        for (String key : new String[]{"infinite_build", "team_sync", "enabled"}) {
            Object[] value = values.get("donutgame:" + key);
            if (value == null || value[0] != PersistentDataType.BYTE || !Byte.valueOf((byte) 1).equals(value[1]))
                throw new AssertionError(key + " must remain a byte flag");
        }
        if (values.get("donutgame:count")[0] != PersistentDataType.INTEGER || !Integer.valueOf(25).equals(values.get("donutgame:count")[1]))
            throw new AssertionError("Integer custom data must remain an integer");
        if (values.containsKey("donutgame:large")) throw new AssertionError("Do not partially parse unsupported numeric suffixes");
        NamespacedKey fuse = NamespacedKey.fromString("donutgame:auto_ignite");
        for (var type : new PersistentDataType[]{PersistentDataType.BYTE, PersistentDataType.SHORT, PersistentDataType.INTEGER, PersistentDataType.LONG}) {
            Number value;
            if (type == PersistentDataType.BYTE) value = Byte.valueOf((byte)80);
            else if (type == PersistentDataType.SHORT) value = Short.valueOf((short)80);
            else if (type == PersistentDataType.INTEGER) value = Integer.valueOf(80);
            else value = Long.valueOf(80);
            var data = (PersistentDataContainer) Proxy.newProxyInstance(ItemNotationChecks.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("has")) return arguments[1] == type;
                    if (method.getName().equals("get")) {
                        if (arguments[1] != type) throw new IllegalArgumentException("Wrong NBT numeric type");
                        return type.getPrimitiveType().cast(value);
                    }
                    return null;
                });
            if (GameItemComponents.integerValue(data, fuse) != 80) throw new AssertionError("Loot fuse must survive numeric NBT encodings");
        }
        System.out.println("Passed byte flag, integer notation, and loot fuse numeric type checks.");
    }
}
