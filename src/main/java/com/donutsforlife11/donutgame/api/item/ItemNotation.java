package com.donutsforlife11.donutgame.api.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class ItemNotation {
    private static final Pattern CUSTOM_DATA_ENTRY_PATTERN =
        Pattern.compile("\"?([A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+)\"?\\s*:\\s*(true|false|-?\\d+)");
    private static final NamespacedKey TEMPORARY_LOOT_TABLE_KEY = new NamespacedKey("donutgame", "temporary_loot_pool");

    private ItemNotation() {
    }

    public static GameItem item(String shorthand) {
        return GameItem.from(itemStack(shorthand));
    }

    public static ItemStack itemStack(String shorthand) {
        ParsedItem parsed = parse(shorthand);
        ItemStack item = Bukkit.getItemFactory().createItemStack(parsed.definition());
        if (parsed.amount() > 0) {
            item.setAmount(parsed.amount());
        }
        applyInlineCustomData(item, parsed.definition());
        return GameItemComponents.normalize(item);
    }

    public static List<GameItem> items(String shorthand) {
        return items(List.of(shorthand));
    }

    public static List<GameItem> items(Collection<String> shorthand) {
        if (shorthand == null) {
            return List.of();
        }
        List<GameItem> items = new ArrayList<>(shorthand.size());
        for (String item : shorthand) {
            items.add(item(item));
        }
        return List.copyOf(items);
    }

    public static LootTable lootTable(String shorthand) {
        return lootTable(List.of(shorthand));
    }

    public static LootTable lootTable(Collection<?> entries) {
        if (entries == null) {
            return new TemporaryLootTable(List.of());
        }
        List<ItemStack> stacks = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (entry instanceof GameItem item) {
                stacks.add(item.copyBukkitItem());
            } else if (entry instanceof ItemStack stack) {
                stacks.add(stack.clone());
            } else if (entry instanceof String string) {
                stacks.add(itemStack(string));
            } else if (entry != null) {
                throw new IllegalArgumentException("Loot table entries must be item strings, GameItems, or ItemStacks.");
            }
        }
        return new TemporaryLootTable(stacks);
    }

    public static Collection<ItemStack> fromLootTable(LootTable lootTable) {
        return fromLootTable(lootTable, ThreadLocalRandom.current().nextLong());
    }

    public static Collection<ItemStack> fromLootTable(LootTable lootTable, long seed) {
        Objects.requireNonNull(lootTable, "lootTable");
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            throw new IllegalStateException("Cannot generate loot without a loaded world.");
        }
        LootContext context = new LootContext.Builder(new Location(world, 0, 0, 0)).build();
        return lootTable.populateLoot(new Random(seed), context);
    }

    private static ParsedItem parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Item shorthand cannot be blank.");
        }
        String trimmed = input.trim();
        int amountStart = trailingAmountStart(trimmed);
        if (amountStart < 0) {
            return new ParsedItem(trimmed, 1);
        }
        return new ParsedItem(
            trimmed.substring(0, amountStart).trim(),
            Integer.parseInt(trimmed.substring(amountStart).trim())
        );
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
        int end = matchingBrace(definition, dataStart);
        if (end < 0) {
            return;
        }

        String customData = definition.substring(dataStart, end);
        item.editPersistentDataContainer(data -> {
            Matcher matcher = CUSTOM_DATA_ENTRY_PATTERN.matcher(customData);
            while (matcher.find()) {
                NamespacedKey key = NamespacedKey.fromString(matcher.group(1));
                if (key == null) {
                    continue;
                }
                setPersistentValue(data, key, matcher.group(2));
            }
        });
    }

    private static int matchingBrace(String input, int start) {
        int depth = 1;
        for (int i = start; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void setPersistentValue(PersistentDataContainer data, NamespacedKey key, String rawValue) {
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            data.set(key, PersistentDataType.BYTE, (byte) (Boolean.parseBoolean(rawValue) ? 1 : 0));
        } else {
            data.set(key, PersistentDataType.INTEGER, Integer.parseInt(rawValue));
        }
    }

    private record ParsedItem(String definition, int amount) {
    }

    private record TemporaryLootTable(List<ItemStack> items) implements LootTable {
        private TemporaryLootTable {
            items = items.stream().map(ItemStack::clone).toList();
        }

        @Override
        public Collection<ItemStack> populateLoot(java.util.Random random, org.bukkit.loot.LootContext context) {
            return items.stream().map(ItemStack::clone).toList();
        }

        @Override
        public void fillInventory(Inventory inventory, java.util.Random random, org.bukkit.loot.LootContext context) {
            if (inventory == null) {
                return;
            }
            for (ItemStack item : populateLoot(random, context)) {
                inventory.addItem(item);
            }
        }

        @Override
        public NamespacedKey getKey() {
            return TEMPORARY_LOOT_TABLE_KEY;
        }
    }
}
