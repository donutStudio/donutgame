package com.donutsforlife11.donutgame.internal.item;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.potion.PotionType;

import com.donutsforlife11.donutgame.api.data.GameItems;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class GameItemService {
    private static final Pattern CUSTOM_DATA_ENTRY_PATTERN =
        Pattern.compile("\"?([A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)\"?\\s*:\\s*(true|false|-?\\d+)");
    private static final Set<String> COLOR_SUFFIXES = Set.of(
        "WOOL", "CONCRETE", "CONCRETE_POWDER", "TERRACOTTA", "GLAZED_TERRACOTTA",
        "STAINED_GLASS", "STAINED_GLASS_PANE", "CARPET", "BED", "BANNER",
        "WALL_BANNER", "CANDLE", "SHULKER_BOX", "DYE"
    );
    // private static final Set<String> TNT_TYPES = Set.of("TNT", "TNT_MINECART");

    private final NamespacedKey teamSyncKey = new NamespacedKey("donutgame", "team_sync");
    private final NamespacedKey infiniteBuildKey = new NamespacedKey("donutgame", "infinite_build");
    private final NamespacedKey infiniteBlocksKey = new NamespacedKey("donutgame", "infinite_blocks");
    private final NamespacedKey autoIgniteKey = new NamespacedKey("donutgame", "auto_ignite");
    private final NamespacedKey autoIgniteNamedKey = new NamespacedKey("donutgame", "auto_ignite_named");

    public ItemStack parseItem(String input) {
        ItemStack item = GameItems.item(input);
        applyDefaults(item);
        return item;
    }

    public LootTable lootTable(List<ItemStack> items) {
        if (items == null) {
            throw new IllegalArgumentException("Loot table items cannot be null.");
        }
        List<ItemStack> normalizedItems = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack clone = item.clone();
            applyDefaults(clone);
            normalizedItems.add(clone);
        }
        return new ItemPoolLootTable(normalizedItems);
    }

    public LootTable lootTable(GameModule module, NamespacedKey key) {
        YamlConfiguration lootTable = loadLootTable(module, key);
        return new ConfigLootTable(key, lootTable);
    }

    private Collection<ItemStack> populateLoot(YamlConfiguration lootTable, Random random) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> pool : lootTable.getMapList("pools")) {
            int rolls = value(pool.get("rolls"), random);
            List<Map<?, ?>> entries = mapList(pool.get("entries"));
            for (int i = 0; i < rolls; i++) {
                ItemStack item = createLootItem(weighted(entries, random), random);
                if (item != null) {
                    applyDefaults(item);
                    items.add(item);
                }
            }
        }
        return items;
    }

    public void give(GamePlayer player, Collection<ItemStack> items) {
        Player bukkitPlayer = player.player();
        if (bukkitPlayer == null || items.isEmpty()) return;
        List<ItemStack> normalizedItems = new ArrayList<>(items.size());
        for (ItemStack item : items) normalizedItems.add(normalizeForInventory(player, item.clone()));
        Map<Integer, ItemStack> leftovers = player.addToStoredInventory(normalizedItems);
        for (ItemStack leftover : leftovers.values()) bukkitPlayer.getWorld().dropItemNaturally(bukkitPlayer.getLocation(), leftover);
    }

    public void normalizeInventory(GamePlayer player) {
        Player bukkitPlayer = player.player();
        if (bukkitPlayer == null) {
            return;
        }
        PlayerInventory inventory = bukkitPlayer.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack normalized = normalizeForInventory(player, item.clone());
            if (!item.equals(normalized)) {
                contents[i] = normalized;
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    public void replenishPlacedBlock(GamePlayer player, ItemStack placedItem, org.bukkit.inventory.EquipmentSlot hand) {
        Player bukkitPlayer = player.player();
        if (bukkitPlayer == null || !hasInfiniteBuild(placedItem)) {
            return;
        }
        PlayerInventory inventory = bukkitPlayer.getInventory();
        ItemStack current = hand == org.bukkit.inventory.EquipmentSlot.HAND
            ? inventory.getItemInMainHand()
            : inventory.getItemInOffHand();
        if (current == null || current.getType() == Material.AIR) {
            ItemStack replacement = placedItem.clone();
            replacement.setAmount(clampedInfiniteAmount(placedItem));
            setHandItem(inventory, hand, replacement);
            return;
        }
        ItemStack replacement = current.clone();
        replacement.setAmount(clampedInfiniteAmount(placedItem));
        setHandItem(inventory, hand, replacement);
    }

    public boolean hasInfiniteBuild(ItemStack item) {
        return getBoolean(item, infiniteBuildKey) || getBoolean(item, infiniteBlocksKey);
    }

    public int autoIgniteFuse(ItemStack item) {
        return getInt(item, autoIgniteKey);
    }

    public Component displayName(ItemStack item) {
        return GameItems.displayName(item);
    }

    public Material mappedDyeMaterial(NamedTextColor color) {
        return Material.getMaterial(mappedDyeColor(color).name() + "_DYE");
    }

    private YamlConfiguration loadLootTable(GameModule module, NamespacedKey key) {
        String path = "data/" + key.namespace() + "/loot_table/" + key.value() + ".json";
        InputStream stream = module.resource(path);
        if (stream == null) {
            throw new IllegalArgumentException("Unknown loot table: " + key + " (missing " + path + ")");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            String text = new BufferedReader(reader).lines().reduce("", (left, right) -> left + right + "\n");
            return YamlConfiguration.loadConfiguration(new java.io.StringReader(text));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unknown loot table: " + key, exception);
        }
    }

    private ItemStack createLootItem(Map<?, ?> entry, Random random) {
        if (entry == null) {
            return null;
        }
        String type = string(entry.get("type"));
        if ("minecraft:empty".equals(type)) {
            return null;
        }
        String name = string(entry.get("name"));
        if (name == null && type != null && type.startsWith("minecraft:") && !"minecraft:item".equals(type)) {
            name = type;
        }
        if (name == null) {
            return null;
        }
        Material material = Registry.MATERIAL.get(NamespacedKey.fromString(name));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material " + name);
        }
        ItemStack item = ItemStack.of(material);
        for (Map<?, ?> function : mapList(entry.get("functions"))) {
            applyFunction(item, function, random);
        }
        return item;
    }

    private void applyFunction(ItemStack item, Map<?, ?> function, Random random) {
        String name = string(function.get("function"));
        if (name == null) {
            return;
        }
        switch (name) {
            case "minecraft:set_count" -> item.setAmount(value(function.get("count"), random));
            case "minecraft:set_damage" -> item.editMeta(Damageable.class, meta -> meta.setDamage(value(function.get("damage"), random)));
            case "minecraft:set_potion" -> item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potionType(function.get("id"))));
            case "minecraft:set_components" -> applyComponents(item, asMap(function.get("components")));
            case "minecraft:set_enchantments" -> item.setItemMeta(withEnchantments(item.getItemMeta(), asMap(function.get("enchantments")), random));
            case "minecraft:enchant_with_levels" -> applyEnchantWithLevels(item, function, random);
            default -> {
            }
        }
    }

    private void applyComponents(ItemStack item, Map<?, ?> components) {
        Object customData = component(components, "minecraft:custom_data", "custom_data");
        if (customData instanceof Map<?, ?> dataMap) {
            item.editMeta(meta -> applyCustomData(meta.getPersistentDataContainer(), dataMap));
        }
        Object potionContents = component(components, "minecraft:potion_contents", "potion_contents");
        Object potion = potionContents instanceof Map<?, ?> potionMap
            ? component(potionMap, "minecraft:potion", "potion")
            : potionContents;
        if (potion != null) {
            item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potionType(potion)));
        }
    }

    private void applyCustomData(PersistentDataContainer data, Map<?, ?> dataMap) {
        for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(String.valueOf(entry.getKey()));
            if (key == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Boolean bool) {
                data.set(key, PersistentDataType.BYTE, bool ? (byte) 1 : (byte) 0);
            } else if (value instanceof Number number) {
                data.set(key, PersistentDataType.INTEGER, number.intValue());
            } else if (value instanceof String string) {
                data.set(key, PersistentDataType.STRING, string);
            }
        }
    }

    private PotionType potionType(Object value) {
        String raw = string(value);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Potion type cannot be blank.");
        }
        NamespacedKey key = namespacedKey(raw);
        if (key != null) {
            PotionType registered = Registry.POTION.get(key);
            if (registered != null) {
                return registered;
            }
        }
        String enumName = stripNamespace(raw).replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return PotionType.valueOf(enumName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown potion type " + raw + " (normalized as " + enumName + ").", exception);
        }
    }

    private ItemMeta withEnchantments(ItemMeta meta, Map<?, ?> enchantments, Random random) {
        for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.fromString(String.valueOf(entry.getKey())));
            if (enchantment == null) {
                continue;
            }
            int level = Math.max(1, Math.min(value(entry.getValue(), random), enchantment.getMaxLevel()));
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        }
        return meta;
    }

    private void applyEnchantWithLevels(ItemStack item, Map<?, ?> function, Random random) {
        int levels = Math.max(1, Math.min(30, value(function.get("levels"), random)));
        List<?> options = list(function.get("options"));
        if (options.isEmpty()) {
            ItemStack enchanted = item.enchantWithLevels(levels, false, random);
            item.setItemMeta(enchanted.getItemMeta());
            return;
        }
        Object selectedOption = options.get(random.nextInt(options.size()));
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.fromString(String.valueOf(selectedOption).toLowerCase(Locale.ROOT)));
        if (enchantment == null) {
            return;
        }
        int level = Math.max(1, Math.min(levels, enchantment.getMaxLevel()));
        item.editMeta(meta -> {
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        });
    }

    private ItemStack normalizeForInventory(GamePlayer player, ItemStack item) {
        applyDefaults(item);
        GameTeam team = player.team();
        if (team == null || !getBoolean(item, teamSyncKey)) {
            return item;
        }
        ItemStack normalized = recolor(item, team.color());
        applyDefaults(normalized);
        return normalized;
    }

    private ItemStack recolor(ItemStack item, NamedTextColor teamColor) {
        org.bukkit.DyeColor dyeColor = mappedDyeColor(teamColor);
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            ItemStack recolored = item.clone();
            recolored.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(toBukkitColor(teamColor.value())));
            return recolored;
        }
        Material recoloredType = recoloredMaterial(item.getType(), dyeColor);
        if (recoloredType == null || recoloredType == item.getType()) {
            return item;
        }
        ItemStack recolored = ItemStack.of(recoloredType, item.getAmount());
        ItemMeta originalMeta = item.getItemMeta();
        if (originalMeta != null) {
            recolored.setItemMeta(originalMeta.clone());
        }
        return recolored;
    }

    private Material recoloredMaterial(Material material, org.bukkit.DyeColor dyeColor) {
        String name = material.name();
        for (org.bukkit.DyeColor current : EnumSet.allOf(org.bukkit.DyeColor.class)) {
            String prefix = current.name() + "_";
            if (!name.startsWith(prefix)) {
                continue;
            }
            String suffix = name.substring(prefix.length());
            if (!COLOR_SUFFIXES.contains(suffix)) {
                return null;
            }
            return Material.getMaterial(dyeColor.name() + "_" + suffix);
        }
        return null;
    }

    private org.bukkit.DyeColor mappedDyeColor(NamedTextColor color) {
        if (color == NamedTextColor.BLACK) {
            return org.bukkit.DyeColor.BLACK;
        }
        if (color == NamedTextColor.DARK_BLUE || color == NamedTextColor.BLUE) {
            return org.bukkit.DyeColor.BLUE;
        }
        if (color == NamedTextColor.DARK_GREEN) {
            return org.bukkit.DyeColor.GREEN;
        }
        if (color == NamedTextColor.DARK_AQUA) {
            return org.bukkit.DyeColor.CYAN;
        }
        if (color == NamedTextColor.DARK_RED) {
            return org.bukkit.DyeColor.BROWN;
        }
        if (color == NamedTextColor.DARK_PURPLE) {
            return org.bukkit.DyeColor.PURPLE;
        }
        if (color == NamedTextColor.GOLD) {
            return org.bukkit.DyeColor.ORANGE;
        }
        if (color == NamedTextColor.GRAY) {
            return org.bukkit.DyeColor.LIGHT_GRAY;
        }
        if (color == NamedTextColor.DARK_GRAY) {
            return org.bukkit.DyeColor.GRAY;
        }
        if (color == NamedTextColor.GREEN) {
            return org.bukkit.DyeColor.LIME;
        }
        if (color == NamedTextColor.AQUA) {
            return org.bukkit.DyeColor.LIGHT_BLUE;
        }
        if (color == NamedTextColor.RED) {
            return org.bukkit.DyeColor.RED;
        }
        if (color == NamedTextColor.LIGHT_PURPLE) {
            return org.bukkit.DyeColor.PINK;
        }
        if (color == NamedTextColor.YELLOW) {
            return org.bukkit.DyeColor.YELLOW;
        }
        return org.bukkit.DyeColor.WHITE;
    }

    private Color toBukkitColor(int rgb) {
        return Color.fromRGB(rgb & 0xFFFFFF);
    }

    private void applyDefaults(ItemStack item) {
        item.editMeta(meta -> {
            if ((getBoolean(item, infiniteBuildKey) || getBoolean(item, infiniteBlocksKey))
                && !meta.hasMaxStackSize()) {
                meta.setMaxStackSize(65);
            }
            if (getInt(item, autoIgniteKey) > 0
                && !meta.hasItemName()
                && !persistentBoolean(meta, autoIgniteNamedKey)) {
                meta.itemName(Component.text("Auto Ignite " + plainItemName(item)));
                setBoolean(meta.getPersistentDataContainer(), autoIgniteNamedKey, true);
            }
        });
    }

    private int clampedInfiniteAmount(ItemStack source) {
        return Math.max(1, Math.min(source.getAmount(), source.getMaxStackSize()));
    }

    private int getInt(ItemStack item, NamespacedKey key) {
        Integer customValue = lookupInteger(item, key);
        if (customValue != null) return customValue;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer value = persistentInt(meta, key);
        return value == null ? 0 : value;
    }

    private boolean getBoolean(ItemStack item, NamespacedKey key) {
        Boolean customValue = lookupBoolean(item, key);
        if (customValue != null) return customValue;
        ItemMeta meta = item.getItemMeta();
        return meta != null && persistentBoolean(meta, key);
    }

    private void setHandItem(PlayerInventory inventory, org.bukkit.inventory.EquipmentSlot hand, ItemStack item) {
        if (hand == org.bukkit.inventory.EquipmentSlot.HAND) {
            inventory.setItemInMainHand(item);
            return;
        }
        inventory.setItemInOffHand(item);
    }

    private Integer lookupInteger(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Integer componentValue = parseInteger(findCustomValue(meta.getAsComponentString(), key));
            if (componentValue != null) return componentValue;
            Integer nbtValue = parseInteger(findCustomValue(meta.getAsString(), key));
            if (nbtValue != null) return nbtValue;
        }
        return findInteger(item.serialize(), key.toString());
    }

    private Boolean lookupBoolean(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Boolean componentValue = parseBoolean(findCustomValue(meta.getAsComponentString(), key));
            if (componentValue != null) return componentValue;
            Boolean nbtValue = parseBoolean(findCustomValue(meta.getAsString(), key));
            if (nbtValue != null) return nbtValue;
        }
        return findBoolean(item.serialize(), key.toString());
    }

    private Integer findInteger(Object node, String key) {
        Object value = findCustomValue(node, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean findBoolean(Object node, String key) {
        Object value = findCustomValue(node, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return null;
    }

    private Object findCustomValue(Object node, NamespacedKey key) {
        if (node instanceof String string) {
            return parseCustomDataValue(string, key);
        }
        return findCustomValue(node, key.toString());
    }

    private Object findCustomValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (key.equals(String.valueOf(entry.getKey()))) {
                    return entry.getValue();
                }
                Object nested = findCustomValue(entry.getValue(), key);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (node instanceof List<?> list) {
            for (Object entry : list) {
                Object nested = findCustomValue(entry, key);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Object parseCustomDataValue(String dataString, NamespacedKey key) {
        String customData = extractCustomDataPayload(dataString);
        if (customData == null) {
            return null;
        }
        Matcher matcher = CUSTOM_DATA_ENTRY_PATTERN.matcher(customData);
        while (matcher.find()) {
            if (!key.toString().equals(matcher.group(1))) {
                continue;
            }
            String rawValue = matcher.group(2);
            if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
                return Boolean.parseBoolean(rawValue);
            }
            return Integer.parseInt(rawValue);
        }
        return null;
    }

    private String extractCustomDataPayload(String dataString) {
        int componentIndex = dataString.indexOf("minecraft:custom_data=");
        if (componentIndex >= 0) {
            int start = dataString.indexOf('{', componentIndex);
            return start < 0 ? null : balancedSection(dataString, start);
        }
        int nbtIndex = dataString.indexOf("custom_data:");
        if (nbtIndex >= 0) {
            int start = dataString.indexOf('{', nbtIndex);
            return start < 0 ? null : balancedSection(dataString, start);
        }
        return null;
    }

    private String balancedSection(String input, int start) {
        int depth = 0;
        for (int i = start; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return input.substring(start + 1, i);
                }
            }
        }
        return null;
    }

    private Map<?, ?> weighted(List<Map<?, ?>> entries, Random random) {
        int totalWeight = 0;
        for (Map<?, ?> entry : entries) {
            totalWeight += Math.max(1, number(entry.get("weight"), 1));
        }
        if (totalWeight <= 0) {
            return null;
        }
        int target = random.nextInt(totalWeight);
        int currentWeight = 0;
        for (Map<?, ?> entry : entries) {
            currentWeight += Math.max(1, number(entry.get("weight"), 1));
            if (target < currentWeight) {
                return entry;
            }
        }
        return null;
    }

    private int value(Object value, Random random) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Map<?, ?> map && "minecraft:uniform".equals(string(map.get("type")))) {
            int min = number(map.get("min"), 1);
            int max = number(map.get("max"), min);
            return random.nextInt(min, max + 1);
        }
        return 1;
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : Collections.emptyList();
    }

    private List<Map<?, ?>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<?, ?>> maps = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                maps.add(map);
            }
        }
        return maps;
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Collections.emptyMap();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object component(Map<?, ?> map, String namespacedKey, String plainKey) {
        if (map.containsKey(namespacedKey)) {
            return map.get(namespacedKey);
        }
        return map.get(plainKey);
    }

    private NamespacedKey namespacedKey(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key != null) {
            return key;
        }
        return NamespacedKey.minecraft(stripNamespace(normalized));
    }

    private String stripNamespace(String value) {
        String normalized = value.trim();
        int separator = normalized.indexOf(':');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    private void setBoolean(PersistentDataContainer data, NamespacedKey key, boolean value) {
        data.set(key, PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0);
    }

    private boolean persistentBoolean(ItemMeta meta, NamespacedKey key) {
        try {
            Byte value = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
            if (value != null) {
                return value != 0;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            Boolean value = meta.getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
            if (value != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            Integer value = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (value != null) {
                return value != 0;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    private Integer persistentInt(ItemMeta meta, NamespacedKey key) {
        try {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return null;
    }

    private String plainItemName(ItemStack item) {
        Component baseName;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemName()) {
            baseName = meta.itemName();
        } else {
            baseName = Bukkit.getItemFactory().displayName(ItemStack.of(item.getType()));
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(baseName).trim();
        if (!plain.isEmpty()) {
            return plain;
        }
        return Arrays.stream(item.getType().name().split("_"))
            .map(part -> part.isEmpty() ? part : part.substring(0, 1) + part.substring(1).toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private class ConfigLootTable implements LootTable {
        private final NamespacedKey key;
        private final YamlConfiguration config;

        private ConfigLootTable(NamespacedKey key, YamlConfiguration config) {
            this.key = key;
            this.config = config;
        }

        @Override
        public Collection<ItemStack> populateLoot(Random random, LootContext context) {
            return GameItemService.this.populateLoot(config, random == null ? ThreadLocalRandom.current() : random);
        }

        @Override
        public void fillInventory(Inventory inventory, Random random, LootContext context) {
            for (ItemStack item : populateLoot(random, context)) {
                inventory.addItem(item);
            }
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }

}
