package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.data.GameItemAttributes;
import com.donutsforlife11.donutgame.api.data.GameItems;
import com.donutsforlife11.donutgame.api.map.GameChest;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsEvents {
    private final VoidWars game;

    private final List<BorderShrinkEvent> borderShrinkEvents = new ArrayList<>();
    private final List<ChestFillEvent> chestFillEvents = new ArrayList<>();
    private final List<ItemDropEvent> itemDropEvents = new ArrayList<>();

    private double borderSpeed = 0.015; // In blocks per tick, scalar. Border will always constantly shrink at this rate on each of its dimension axes until it hits the target dimensions
    private List<GameChest> chests = new ArrayList<>();

    public VoidWarsEvents(VoidWars game) {
        this.game = game;
    }

    public void addEventActions() {
        chests = new ArrayList<>(spawnChests());
        game.mainTimer().onTick(20, timer -> {
            if (timer.getElapsedTicks() == game.groundCollapseTime) {
                for (GameRegion region : game.world().getRegions("spawn_platform")) {
                    game.world().fill(region, Material.AIR);
                    game.uiManager().playSound(game.playerManager().getPlayers(), Sound.ENTITY_WARDEN_DEATH, 1f, 0.5f);
                }
                if (timer.getElapsedTicks() > 0) {
                    game.uiManager().gameMessage(game.playerManager().getPlayers(), Component.text("The ground has collapsed!"));
                }
            }
            if (timer.getElapsedTicks() == game.pvpEnablementTime) {
                game.world().setPvp(true);
                if (timer.getElapsedTicks() > 0) {
                    game.uiManager().playSound(game.playerManager().getPlayers(), Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.75f);
                    game.uiManager().gameMessage(game.playerManager().getPlayers(), Component.text("PvP is now enabled!"));
                }
            }
            for (BorderShrinkEvent event : borderShrinkEvents) {
                if (timer.getElapsedTicks() == event.ticks()) {
                    shrinkBorder(event.scale());
                }
            }
            for (ChestFillEvent event : chestFillEvents) {
                if (timer.getElapsedTicks() == event.ticks()) {
                    fillChests(event.lootTable());
                }
            }
            for (ItemDropEvent event : itemDropEvents) {
                if (timer.getElapsedTicks() == event.ticks()) {
                    dropItem(event.lootTable());
                }
            }
        });
    }

    public void shrinkBorder(double scale) {
        GameBorder border = game.mainBorder();
        if (border == null) {
            return;
        }
        Vector dimensions = border.dimensions();
        Vector target = dimensions.clone().multiply(scale);
        double maxChange = Math.max(
            Math.abs(dimensions.getX() - target.getX()),
            Math.max(Math.abs(dimensions.getY() - target.getY()), Math.abs(dimensions.getZ() - target.getZ()))
        );
        int ticks = borderSpeed <= 0 || maxChange <= 0 ? 0 : Math.max(1, (int) Math.ceil(maxChange / borderSpeed));
        border.setDimensions(target, ticks);
        game.uiManager().subtitle(game.playerManager().getPlayers(), Component.empty()
            .append(Component.text("! ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("Border Shrinking", NamedTextColor.RED))
            .append(Component.text(" !", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        );
        game.uiManager().playSound(game.playerManager().getPlayers(), Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.75f);
    }
    public void fillChests(LootTable lootTable) {
        int multiplier = Math.max(1, game.teamSize);
        for (GameChest chest : chests) {
            chest.ensurePresent();
            for (int i = 0; i < multiplier; i++) {
                chest.addLootTable(lootTable);
            }
        }
        if (game.mainTimer().getElapsedTicks() > 0) {
            game.uiManager().subtitle(game.playerManager().getPlayers(), Component.empty()
            .append(Component.text("! ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            .append(Component.text("Chests Refilled", NamedTextColor.GREEN))
            .append(Component.text(" !", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            );
            game.uiManager().playSound(game.playerManager().getPlayers(), Sound.BLOCK_CHEST_OPEN, 1f, 1.25f);
        }
    }
    public void dropItem(LootTable lootTable) {
        Collection<ItemStack> items = GameItems.items(lootTable);
        if (items.isEmpty()) {
            return;
        }
        for (GamePlayer player : game.playerManager().getPlayers()) {
            for (ItemStack item : items) {
                player.giveItem(GameItemAttributes.teamSyncedItem(player, item));
            }
        }
        if (game.mainTimer().getElapsedTicks() > 0) {
            for (ItemStack item : items) {
                game.uiManager().gameMessage(game.playerManager().getPlayers(), Component.text("Gave players ")
                    .append(GameItems.displayName(item))
                    .append(Component.text(" x" + item.getAmount()))
                );
            }
            game.uiManager().playSound(game.playerManager().getPlayers(), Sound.ENTITY_ITEM_PICKUP, 1f, 0f);
        }
    }

    public List<GameChest> spawnChests() {
        List<GameLocation> points = game.world().getPoints("chest");
        if (points.isEmpty()) {
            game.logWarning("Map " + game.mapManager().map().id() + " has no chest points; no chests will spawn.");
            return List.of();
        }
        Collections.shuffle(points);
        int min = Math.max(0, game.minChests);
        int max = Math.max(min, game.maxChests);
        int count = Math.min(points.size(), ThreadLocalRandom.current().nextInt(min, max + 1));
        List<GameChest> spawnedChests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GameChest chest = game.world().newChest(points.get(i));
            chest.clear();
            spawnedChests.add(chest);
        }
        return spawnedChests;
    }

    public void populateEventLists() {
        itemDropEvents.clear();
        chestFillEvents.clear();
        borderShrinkEvents.clear();
        YamlConfiguration eventConfig = game.config(game.eventConfig);
        itemDropEvents.addAll(loadLootEvents(eventConfig, "item_drops", (time, lootTable) -> new ItemDropEvent(time, lootTable)));
        chestFillEvents.addAll(loadLootEvents(eventConfig, "chest_fills", (time, lootTable) -> new ChestFillEvent(time, lootTable)));
        borderShrinkEvents.addAll(loadBorderEvents(eventConfig, "border_shrinks"));
    }

    private <T> List<T> loadLootEvents(YamlConfiguration eventConfig, String key, BiFunction<Integer, LootTable, T> constructor) {
        List<T> events = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : eventConfig.getMapList(key)) {
            String path = key + "[" + index + "]";
            int time = requiredNonNegativeInt(entry, "time", path);
            LootTable lootTable;
            if (entry.containsKey("loot_table")) {
                lootTable = game.data().lootTable(requiredString(entry, "loot_table", path));
            } else {
                List<String> pool = new ArrayList<>();
                Object rawPool = entry.get("pool");
                if (!(rawPool instanceof List<?> rawItems)) {
                    throw new IllegalStateException("Void Wars events.yml value '" + path + ".pool' must be a list when loot_table is not set.");
                }
                int itemIndex = 0;
                for (Object item : rawItems) {
                    if (!(item instanceof String itemString) || itemString.isBlank()) {
                        throw new IllegalStateException("Void Wars events.yml value '" + path + ".pool[" + itemIndex + "]' must be a non-blank string.");
                    }
                    pool.add(itemString);
                    itemIndex++;
                }
                lootTable = game.data().lootTable(GameItems.items(pool));
            }
            events.add(constructor.apply(time * 20, lootTable));
            index++;
        }
        return events;
    }
    private List<BorderShrinkEvent> loadBorderEvents(YamlConfiguration eventConfig, String key) {
        List<BorderShrinkEvent> events = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : eventConfig.getMapList(key)) {
            String path = key + "[" + index + "]";
            int time = requiredNonNegativeInt(entry, "time", path);
            double scale = requiredDouble(entry, "scale", path);
            if (scale < 0.0 || scale > 1.0) {
                throw new IllegalStateException("Void Wars events.yml value '" + path + ".scale' must be between 0.0 and 1.0.");
            }
            events.add(new BorderShrinkEvent(time * 20, scale));
            index++;
        }
        return events;
    }

    private int requiredNonNegativeInt(Map<?, ?> entry, String key, String path) {
        Object value = entry.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Void Wars events.yml value '" + path + "." + key + "' must be a number.");
        }
        int intValue = number.intValue();
        if (intValue < 0) {
            throw new IllegalStateException("Void Wars events.yml value '" + path + "." + key + "' cannot be negative.");
        }
        return intValue;
    }

    private double requiredDouble(Map<?, ?> entry, String key, String path) {
        Object value = entry.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Void Wars events.yml value '" + path + "." + key + "' must be a number.");
        }
        return number.doubleValue();
    }

    private String requiredString(Map<?, ?> entry, String key, String path) {
        Object value = entry.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("Void Wars events.yml value '" + path + "." + key + "' must be a non-blank string.");
        }
        return string;
    }

    public String nextEventLabel() {
        int elapsedTicks = game.mainTimer() == null ? 0 : game.mainTimer().getElapsedTicks();
        if (elapsedTicks < game.groundCollapseTime) {
            return "Ground Collapse";
        } else if (elapsedTicks < game.pvpEnablementTime) {
            return "PvP Enabling";
        }
        int nextTicks = Integer.MAX_VALUE;
        String nextLabel = "Waiting";
        for (BorderShrinkEvent event : borderShrinkEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                nextLabel = "Border Shrink";
            }
        }
        for (ChestFillEvent event : chestFillEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                nextLabel = "Chest Refill";
            }
        }
        for (ItemDropEvent event : itemDropEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
                nextLabel = "Item Drop";
            }
        }
        return nextLabel;
    }
    public int nextEventTime() {
        int elapsedTicks = game.mainTimer() == null ? 0 : game.mainTimer().getElapsedTicks();
        if (elapsedTicks < game.groundCollapseTime) {
            return game.groundCollapseTime - elapsedTicks;
        } else if (elapsedTicks < game.pvpEnablementTime) {
            return game.pvpEnablementTime - elapsedTicks;
        }
        int nextTicks = Integer.MAX_VALUE;
        for (BorderShrinkEvent event : borderShrinkEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
            }
        }
        for (ChestFillEvent event : chestFillEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
            }
        }
        for (ItemDropEvent event : itemDropEvents) {
            if (elapsedTicks < event.ticks() && event.ticks() < nextTicks) {
                nextTicks = event.ticks();
            }
        }
        return nextTicks == Integer.MAX_VALUE ? 0 : nextTicks - elapsedTicks;
    }

    public record BorderShrinkEvent(int ticks, double scale) {
    }
    public record ChestFillEvent(int ticks, LootTable lootTable) {
    }
    public record ItemDropEvent(int ticks, LootTable lootTable) {
    }
}
