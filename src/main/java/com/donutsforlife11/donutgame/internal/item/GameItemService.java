package com.donutsforlife11.donutgame.internal.item;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.configuration.file.YamlConfiguration;

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
    private final Map<String, YamlConfiguration> lootTables = new HashMap<>();

    public ItemStack parseItem(String input) {
        ParsedItem parsedItem = normalizeInput(input);
        ItemStack item = Bukkit.getItemFactory().createItemStack(parsedItem.definition());
        applyInlineCustomData(item, parsedItem.definition());
        if (parsedItem.amount() > 0) {
            item.setAmount(parsedItem.amount());
        }
        applyDefaults(item);
        return item;
    }

    public ItemStack randomPool(List<String> pool) {
        return parseItem(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
    }

    public Collection<ItemStack> loot(GameModule module, String path, GamePlayer player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        YamlConfiguration lootTable = lootTables.computeIfAbsent(module.id() + ":" + path, ignored -> loadLootTable(module, path));
        List<Map<?, ?>> pools = lootTable.getMapList("pools");
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> pool : pools) {
            int rolls = value(pool.get("rolls"), random);
            List<Map<?, ?>> entries = mapList(pool.get("entries"));
            for (int i = 0; i < rolls; i++) {
                Map<?, ?> entry = weighted(entries, random);
                if (entry == null) {
                    continue;
                }
                ItemStack item = createLootItem(entry, random);
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
        if (bukkitPlayer == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> normalizedItems = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            normalizedItems.add(normalizeForInventory(player, item.clone()));
        }
        Map<Integer, ItemStack> leftovers = bukkitPlayer.getInventory().addItem(normalizedItems.toArray(ItemStack[]::new));
        for (ItemStack leftover : leftovers.values()) {
            bukkitPlayer.getWorld().dropItemNaturally(bukkitPlayer.getLocation(), leftover);
        }
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
        removeFiniteDuplicates(inventory);
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
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemName()) {
            return meta.itemName();
        }
        return Bukkit.getItemFactory().displayName(item);
    }

    private YamlConfiguration loadLootTable(GameModule module, String path) {
        try (Reader reader = module.data().reader("data/loot_table/" + path + ".json")) {
            String text = new BufferedReader(reader).lines().reduce("", (left, right) -> left + right + "\n");
            return YamlConfiguration.loadConfiguration(new java.io.StringReader(text));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load loot table " + path + " for module " + module.id(), e);
        }
    }

    private ItemStack createLootItem(Map<?, ?> entry, ThreadLocalRandom random) {
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

    private void applyFunction(ItemStack item, Map<?, ?> function, ThreadLocalRandom random) {
        String name = string(function.get("function"));
        if (name == null) {
            return;
        }
        switch (name) {
            case "minecraft:set_count" -> item.setAmount(value(function.get("count"), random));
            case "minecraft:set_damage" -> item.editMeta(Damageable.class, meta -> meta.setDamage(value(function.get("damage"), random)));
            case "minecraft:set_potion" -> item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.valueOf(string(function.get("id")).toUpperCase(Locale.ROOT))));
            case "minecraft:set_components" -> applyComponents(item, asMap(function.get("components")));
            case "minecraft:set_enchantments" -> item.setItemMeta(withEnchantments(item.getItemMeta(), asMap(function.get("enchantments")), random));
            case "minecraft:enchant_with_levels" -> applyEnchantWithLevels(item, function, random);
            default -> {
            }
        }
    }

    private void applyComponents(ItemStack item, Map<?, ?> components) {
        if (components.isEmpty()) {
            return;
        }
        item.editMeta(meta -> {
            Object maxStackSize = components.get("minecraft:max_stack_size");
            if (maxStackSize instanceof Number number) {
                meta.setMaxStackSize(number.intValue());
            }
            Object customData = components.get("minecraft:custom_data");
            if (customData instanceof Map<?, ?> customDataMap) {
                PersistentDataContainer data = meta.getPersistentDataContainer();
                for (Map.Entry<?, ?> entry : customDataMap.entrySet()) {
                    NamespacedKey key = NamespacedKey.fromString(String.valueOf(entry.getKey()));
                    Object value = entry.getValue();
                    if (key == null || value == null) {
                        continue;
                    }
                    if (value instanceof Boolean bool) {
                        setBoolean(data, key, bool);
                        continue;
                    }
                    if (value instanceof Number number) {
                        data.set(key, PersistentDataType.INTEGER, number.intValue());
                    }
                }
            }
        });
    }

    private ItemMeta withEnchantments(ItemMeta meta, Map<?, ?> enchantments, ThreadLocalRandom random) {
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

    private void applyEnchantWithLevels(ItemStack item, Map<?, ?> function, ThreadLocalRandom random) {
        int levels = Math.max(1, Math.min(30, value(function.get("levels"), random)));
        List<?> options = list(function.get("options"));
        if (options.isEmpty()) {
            ItemStack enchanted = item.enchantWithLevels(levels, false, random);
            item.setItemMeta(Objects.requireNonNull(enchanted.getItemMeta()));
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
        mirrorCustomData(item);
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

    private void removeFiniteDuplicates(PlayerInventory inventory) {
        Set<Material> infiniteTypes = EnumSet.noneOf(Material.class);
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR && hasInfiniteBuild(item)) {
                infiniteTypes.add(item.getType());
            }
        }
        if (infiniteTypes.isEmpty()) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR || hasInfiniteBuild(item) || !infiniteTypes.contains(item.getType())) {
                continue;
            }
            contents[i] = null;
            changed = true;
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    private void applyDefaults(ItemStack item) {
        mirrorCustomData(item);
        item.editMeta(meta -> {
            if ((persistentBoolean(meta, infiniteBuildKey) || persistentBoolean(meta, infiniteBlocksKey))
                && !meta.hasMaxStackSize()) {
                meta.setMaxStackSize(65);
            }
            if (persistentInt(meta, autoIgniteKey) != null
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
        mirrorCustomData(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            Integer value = lookupInteger(item, key);
            return value == null ? 0 : value;
        }
        Integer value = persistentInt(meta, key);
        if (value != null) {
            return value;
        }
        Integer mirroredValue = lookupInteger(item, key);
        return mirroredValue == null ? 0 : mirroredValue;
    }

    private boolean getBoolean(ItemStack item, NamespacedKey key) {
        mirrorCustomData(item);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && persistentBoolean(meta, key)) {
            return true;
        }
        return lookupBoolean(item, key);
    }

    private void setHandItem(PlayerInventory inventory, org.bukkit.inventory.EquipmentSlot hand, ItemStack item) {
        if (hand == org.bukkit.inventory.EquipmentSlot.HAND) {
            inventory.setItemInMainHand(item);
            return;
        }
        inventory.setItemInOffHand(item);
    }

    private Map<?, ?> weighted(List<Map<?, ?>> entries, ThreadLocalRandom random) {
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

    private int value(Object value, ThreadLocalRandom random) {
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

    private ParsedItem normalizeInput(String input) {
        String normalized = input.trim();
        int amount = 1;
        int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace > 0) {
            String trailing = normalized.substring(lastSpace + 1);
            try {
                amount = Integer.parseInt(trailing);
                normalized = normalized.substring(0, lastSpace);
            } catch (NumberFormatException ignored) {
            }
        }
        if (normalized.contains("custom_data={") && normalized.endsWith("]") && !normalized.contains("}]")) {
            normalized = normalized.replace("]", "}]");
        }
        return new ParsedItem(normalized, amount);
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

    private void applyInlineCustomData(ItemStack item, String definition) {
        int start = definition.indexOf("custom_data={");
        if (start < 0) {
            return;
        }
        int dataStart = start + "custom_data={".length();
        int depth = 1;
        int end = -1;
        for (int i = dataStart; i < definition.length(); i++) {
            char current = definition.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) {
            return;
        }
        String customData = definition.substring(dataStart, end);
        item.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            applyCustomDataString(data, customData);
        });
    }

    private void mirrorCustomData(ItemStack item) {
        Map<NamespacedKey, Object> mirroredValues = new LinkedHashMap<>();
        collectMirroredValue(item, teamSyncKey, mirroredValues);
        collectMirroredValue(item, infiniteBuildKey, mirroredValues);
        collectMirroredValue(item, infiniteBlocksKey, mirroredValues);
        collectMirroredValue(item, autoIgniteKey, mirroredValues);
        if (mirroredValues.isEmpty()) {
            return;
        }
        item.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            for (Map.Entry<NamespacedKey, Object> entry : mirroredValues.entrySet()) {
                if (entry.getValue() instanceof Boolean bool) {
                    setBoolean(data, entry.getKey(), bool);
                } else if (entry.getValue() instanceof Integer number) {
                    data.set(entry.getKey(), PersistentDataType.INTEGER, number);
                }
            }
        });
    }

    private void collectMirroredValue(ItemStack item, NamespacedKey key, Map<NamespacedKey, Object> mirroredValues) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Object componentValue = findCustomValue(meta.getAsComponentString(), key);
            if (componentValue != null) {
                mirroredValues.put(key, componentValue);
                return;
            }
            Object nbtValue = findCustomValue(meta.getAsString(), key);
            if (nbtValue != null) {
                mirroredValues.put(key, nbtValue);
                return;
            }
        }
        Boolean bool = findBoolean(item.serialize(), key.toString());
        if (bool != null) {
            mirroredValues.put(key, bool);
            return;
        }
        Integer integer = findInteger(item.serialize(), key.toString());
        if (integer != null) {
            mirroredValues.put(key, integer);
        }
    }

    private Integer lookupInteger(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Integer componentValue = parseInteger(findCustomValue(meta.getAsComponentString(), key));
            if (componentValue != null) {
                return componentValue;
            }
            Integer nbtValue = parseInteger(findCustomValue(meta.getAsString(), key));
            if (nbtValue != null) {
                return nbtValue;
            }
        }
        return findInteger(item.serialize(), key.toString());
    }

    private boolean lookupBoolean(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Boolean componentValue = parseBoolean(findCustomValue(meta.getAsComponentString(), key));
            if (Boolean.TRUE.equals(componentValue)) {
                return true;
            }
            Boolean nbtValue = parseBoolean(findCustomValue(meta.getAsString(), key));
            if (Boolean.TRUE.equals(nbtValue)) {
                return true;
            }
        }
        return Boolean.TRUE.equals(findBoolean(item.serialize(), key.toString()));
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

    private void applyCustomDataString(PersistentDataContainer data, String customData) {
        Matcher matcher = CUSTOM_DATA_ENTRY_PATTERN.matcher(customData);
        while (matcher.find()) {
            NamespacedKey key = NamespacedKey.fromString(matcher.group(1));
            String rawValue = matcher.group(2);
            if (key == null) {
                continue;
            }
            if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
                setBoolean(data, key, Boolean.parseBoolean(rawValue));
            } else {
                data.set(key, PersistentDataType.INTEGER, Integer.parseInt(rawValue));
            }
        }
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

    private record ParsedItem(String definition, int amount) {
    }
}
