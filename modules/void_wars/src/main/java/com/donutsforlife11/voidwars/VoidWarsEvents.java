package com.donutsforlife11.voidwars;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItems;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.object.BlockSpec;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class VoidWarsEvents {
    private static final String EVENTS_RESOURCE = "events.yml";

    private final VoidWars game;
    private final List<GameLocation> chests = new ArrayList<>();
    private final List<TimedLoot> itemDrops = new ArrayList<>();
    private final List<TimedLoot> chestFills = new ArrayList<>();
    private final List<BorderShrink> borderShrinks = new ArrayList<>();

    VoidWarsEvents(VoidWars game) {
        this.game = game;
    }

    void load() {
        itemDrops.clear();
        chestFills.clear();
        borderShrinks.clear();
        YamlConfiguration events = loadEventConfig();
        for (Map<?, ?> entry : events.getMapList("item_drops")) {
            itemDrops.add(new TimedLoot(seconds(entry, "time") * 20, loot(entry)));
        }
        for (Map<?, ?> entry : events.getMapList("chest_fills")) {
            chestFills.add(new TimedLoot(seconds(entry, "time") * 20, loot(entry)));
        }
        for (Map<?, ?> entry : events.getMapList("border_shrinks")) {
            borderShrinks.add(new BorderShrink(seconds(entry, "time") * 20, number(entry, "scale", 1.0)));
        }
    }

    void prepareRound() {
        chests.clear();
        spawnChests();
    }

    void tickRound(int elapsed) {
        if (elapsed == game.groundCollapseTicks) {
            collapseSpawn();
        }
        if (elapsed == game.pvpTicks) {
            game.world().gamerules().pvp(true);
            if (elapsed > 0) {
                game.sound(Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.75f);
                game.gameMessage(Component.text("PvP is now enabled!"));
            }
        }
        itemDrops.stream().filter(event -> event.ticks() == elapsed).forEach(event -> dropItems(event.lootTable()));
        chestFills.stream().filter(event -> event.ticks() == elapsed).forEach(event -> fillChests(event.lootTable()));
        borderShrinks.stream().filter(event -> event.ticks() == elapsed).forEach(event -> shrinkBorder(event.scale()));
    }

    int nextEventTicks() {
        int elapsed = game.roundTimer() == null ? 0 : game.roundTimer().elapsedTicks();
        return allEventTicks().stream()
            .filter(ticks -> ticks > elapsed)
            .min(Integer::compareTo)
            .map(ticks -> ticks - elapsed)
            .orElse(0);
    }

    String nextEventLabel() {
        int elapsed = game.roundTimer() == null ? 0 : game.roundTimer().elapsedTicks();
        if (elapsed < game.groundCollapseTicks) return "Ground Collapse";
        if (elapsed < game.pvpTicks) return "PvP Enabling";
        int nextTicks = Integer.MAX_VALUE;
        String label = "Waiting";
        for (TimedLoot event : itemDrops) {
            if (event.ticks() > elapsed && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                label = "Item Drop";
            }
        }
        for (TimedLoot event : chestFills) {
            if (event.ticks() > elapsed && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                label = "Chest Refill";
            }
        }
        for (BorderShrink event : borderShrinks) {
            if (event.ticks() > elapsed && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                label = "Border Shrink";
            }
        }
        return label;
    }

    private YamlConfiguration loadEventConfig() {
        InputStream input = VoidWars.class.getClassLoader().getResourceAsStream(EVENTS_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Void Wars module is missing " + EVENTS_RESOURCE + ".");
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private LootTable loot(Map<?, ?> entry) {
        Object table = entry.get("loot_table");
        if (table instanceof String key && !key.isBlank()) {
            return game.data().lootTable(key);
        }
        Object pool = entry.get("pool");
        if (pool instanceof List<?> values) {
            return game.data().lootPool(values.stream().map(String::valueOf).toList());
        }
        throw new IllegalStateException("Void Wars event must define loot_table or pool.");
    }

    private int seconds(Map<?, ?> entry, String key) {
        return Math.max(0, (int) number(entry, key, 0));
    }

    private double number(Map<?, ?> entry, String key, double fallback) {
        Object value = entry.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void collapseSpawn() {
        for (GameRegion region : game.world().getRegions(VoidWars.SPAWN_PLATFORM)) {
            game.world().fill(region, BlockSpec.of(Material.AIR));
            game.sound(Sound.ENTITY_WARDEN_DEATH, 1f, 0.5f);
        }
        if (game.roundTimer() != null && game.roundTimer().elapsedTicks() > 0) {
            game.gameMessage(Component.text("The ground has collapsed!"));
        }
    }

    private void spawnChests() {
        List<GameLocation> points = new ArrayList<>(game.world().getPoints(VoidWars.CHEST));
        Collections.shuffle(points);
        int count = points.isEmpty() ? 0 : Math.min(points.size(), ThreadLocalRandom.current().nextInt(game.minChests, game.maxChests + 1));
        for (int i = 0; i < count; i++) {
            GameLocation location = points.get(i);
            game.world().setBlock(location, BlockSpec.of(Material.CHEST));
            chests.add(location);
        }
    }

    private void fillChests(LootTable lootTable) {
        for (GameLocation location : chests) {
            Block block = location.bukkitLocation().getBlock();
            if (block.getType() != Material.CHEST) {
                game.world().setBlock(location, BlockSpec.of(Material.CHEST));
                block = location.bukkitLocation().getBlock();
            }
            if (block.getState() instanceof Chest chest) {
                chest.getBlockInventory().clear();
                chest.setLootTable(lootTable);
                chest.setSeed(ThreadLocalRandom.current().nextLong());
                chest.update(true);
            }
        }
        if (game.roundTimer() != null && game.roundTimer().elapsedTicks() > 0) {
            game.subtitle(game.playerManager().getPlayers(), Component.empty()
                .append(Component.text("! ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
                .append(Component.text("Chests Refilled", NamedTextColor.GREEN))
                .append(Component.text(" !", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            );
            game.sound(Sound.BLOCK_CHEST_OPEN, 1f, 1.25f);
        }
    }

    private void dropItems(LootTable lootTable) {
        Collection<ItemStack> items = GameItems.fromLootTable(lootTable);
        for (GamePlayer player : game.playerManager().getPlayers()) {
            for (ItemStack item : items) {
                player.giveItem(GameItem.from(item));
            }
        }
        if (game.roundTimer() != null && game.roundTimer().elapsedTicks() > 0 && !items.isEmpty()) {
            for (ItemStack item : items) {
                game.gameMessage(Component.text("Gave players ")
                    .append(displayName(item))
                    .append(Component.text(" x" + item.getAmount()))
                );
            }
            game.sound(Sound.ENTITY_ITEM_PICKUP, 1f, 0f);
        }
    }

    private void shrinkBorder(double scale) {
        GameBorder border = game.border();
        if (border == null) {
            return;
        }
        Vector dimensions = border.dimensions();
        Vector target = dimensions.clone().multiply(Math.clamp(scale, 0.0, 1.0));
        double maxChange = Math.max(
            Math.abs(dimensions.getX() - target.getX()),
            Math.max(Math.abs(dimensions.getY() - target.getY()), Math.abs(dimensions.getZ() - target.getZ()))
        );
        border.setDimensions(target, Math.max(1, (int) Math.ceil(maxChange / 0.015)));
        game.subtitle(game.playerManager().getPlayers(), Component.empty()
            .append(Component.text("! ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("Border Shrinking", NamedTextColor.RED))
            .append(Component.text(" !", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        );
        game.sound(Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.75f);
    }

    private List<Integer> allEventTicks() {
        List<Integer> ticks = new ArrayList<>();
        ticks.add(game.groundCollapseTicks);
        ticks.add(game.pvpTicks);
        itemDrops.forEach(event -> ticks.add(event.ticks()));
        chestFills.forEach(event -> ticks.add(event.ticks()));
        borderShrinks.forEach(event -> ticks.add(event.ticks()));
        return ticks;
    }

    private Component displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemName()) {
            return meta.itemName();
        }
        return Bukkit.getItemFactory().displayName(item);
    }

    private record TimedLoot(int ticks, LootTable lootTable) {
    }

    private record BorderShrink(int ticks, double scale) {
    }
}
