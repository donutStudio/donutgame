package com.donutsforlife11.donutgame.api.data;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

public final class GameItems {
    private static final Pattern CUSTOM_DATA_ENTRY_PATTERN =
        Pattern.compile("\"?([A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)\"?\\s*:\\s*(true|false|-?\\d+)");

    private GameItems() {
    }

    public static ItemStack item(String input) {
        ParsedItem parsed = parseInput(input);
        ItemStack item = Bukkit.getItemFactory().createItemStack(parsed.definition());
        if (parsed.amount() > 0) {
            item.setAmount(parsed.amount());
        }
        applyInlineCustomData(item, parsed.definition());
        return item;
    }

    public static List<ItemStack> items(List<String> inputs) {
        return inputs.stream().map(GameItems::item).toList();
    }

    public static Collection<ItemStack> items(LootTable lootTable) {
        return items(lootTable, ThreadLocalRandom.current().nextLong());
    }

    public static Collection<ItemStack> items(LootTable lootTable, long seed) {
        if (lootTable == null) {
            throw new IllegalArgumentException("Loot table cannot be null.");
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            throw new IllegalStateException("Cannot generate loot without a loaded world.");
        }
        LootContext context = new LootContext.Builder(new Location(world, 0, 0, 0)).build();
        return lootTable.populateLoot(new Random(seed), context);
    }

    public static Component displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemName()) {
            return meta.itemName();
        }
        return Bukkit.getItemFactory().displayName(item);
    }

    private static ParsedItem parseInput(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Item input cannot be blank.");
        }
        String trimmed = input.trim();
        int amountStart = trailingAmountStart(trimmed);
        if (amountStart < 0) {
            return new ParsedItem(trimmed, 1);
        }
        return new ParsedItem(trimmed.substring(0, amountStart).trim(), Integer.parseInt(trimmed.substring(amountStart).trim()));
    }

    private static int trailingAmountStart(String input) {
        int end = input.length() - 1;
        while (end >= 0 && Character.isWhitespace(input.charAt(end))) {
            end--;
        }
        int start = end;
        while (start >= 0 && Character.isDigit(input.charAt(start))) {
            start--;
        }
        if (start == end || start < 0 || !Character.isWhitespace(input.charAt(start))) {
            return -1;
        }
        return start + 1;
    }

    private static void applyInlineCustomData(ItemStack item, String definition) {
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
            Matcher matcher = CUSTOM_DATA_ENTRY_PATTERN.matcher(customData);
            while (matcher.find()) {
                NamespacedKey key = NamespacedKey.fromString(matcher.group(1));
                if (key == null) {
                    continue;
                }
                String rawValue = matcher.group(2);
                if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
                    data.set(key, PersistentDataType.BYTE, Boolean.parseBoolean(rawValue) ? (byte) 1 : (byte) 0);
                } else {
                    data.set(key, PersistentDataType.INTEGER, Integer.parseInt(rawValue));
                }
            }
        });
    }

    private record ParsedItem(String definition, int amount) {
    }
}
