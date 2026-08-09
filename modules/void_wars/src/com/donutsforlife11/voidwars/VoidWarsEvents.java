package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.map.GameChest;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsEvents {
    private final VoidWars game;
    private final List<SupplyEvent> itemDrops = new ArrayList<>();
    private final List<SupplyEvent> chestFills = new ArrayList<>();
    private final List<BorderEvent> borders = new ArrayList<>();
    private double borderBlocksPerTick = -1;

    public VoidWarsEvents(VoidWars game) {
        this.game = game;
    }

    public void bind() {
        game.events().player(BlockPlaceEvent.class, event -> event.getPlayer(), wrapped -> {
            if (!mutable(wrapped.event().getBlockPlaced().getLocation())) wrapped.event().setCancelled(true);
        });
        game.events().player(BlockBreakEvent.class, event -> event.getPlayer(), wrapped -> {
            if (!mutable(wrapped.event().getBlock().getLocation())) wrapped.event().setCancelled(true);
        });
        game.events().location(EntityExplodeEvent.class, event -> event.getLocation(), wrapped -> filterExplosion(wrapped.event().blockList()));
        game.events().location(BlockExplodeEvent.class, event -> event.getBlock().getLocation(), wrapped -> filterExplosion(wrapped.event().blockList()));
    }

    public void startRound() {
        borderBlocksPerTick = -1;
        GameTimer timer = game.timeManager().newTimer();
        timer.onTick(20, current -> tick(current.getElapsedTicks() / 20));
        runInstantEvents();
        game.startTimer(timer);
    }

    public List<GameChest> spawnChests() {
        List<GameLocation> points = new ArrayList<>(game.world().getPoints("chest"));
        if (points.isEmpty()) return List.of();
        Collections.shuffle(points);
        int min = Math.max(0, game.config().getInt("min_chests"));
        int max = Math.max(min, game.config().getInt("max_chests"));
        int count = Math.min(points.size(), ThreadLocalRandom.current().nextInt(min, max + 1));
        List<GameChest> chests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GameChest chest = game.world().placeChest(points.get(i));
            chest.clear();
            chests.add(chest);
        }
        return chests;
    }

    public SidebarEvent sidebarEvent() {
        int seconds = game.roundElapsedSeconds();
        int collapseTime = game.config().getInt("ground_collapse_time");
        if (seconds < collapseTime) return new SidebarEvent("Ground Collapse", collapseTime - seconds);
        int pvpTime = game.config().getInt("pvp_enablement_time");
        if (seconds < pvpTime) return new SidebarEvent("PvP Enablement", pvpTime - seconds);
        TimedLabel next = nextTimedEvent(seconds);
        return next == null ? null : new SidebarEvent(next.label(), next.time() - seconds);
    }

    public void loadConfiguredEvents() {
        itemDrops.clear();
        chestFills.clear();
        borders.clear();
        borderBlocksPerTick = -1;
        for (Map<?, ?> entry : game.eventsConfig().getMapList("item_drops")) itemDrops.add(readSupply(entry));
        for (Map<?, ?> entry : game.eventsConfig().getMapList("chest_fills")) chestFills.add(readSupply(entry));
        for (Map<?, ?> entry : game.eventsConfig().getMapList("borders")) borders.add(new BorderEvent(number(entry.get("time")), decimal(entry.get("scale"))));
    }

    private void tick(int seconds) {
        if (!game.roundActive()) return;
        if (seconds == game.config().getInt("ground_collapse_time")) game.collapseGround();
        if (seconds == game.config().getInt("pvp_enablement_time")) game.enablePvp();
        for (SupplyEvent event : itemDrops) if (event.time() == seconds) givePlayers(event);
        for (SupplyEvent event : chestFills) if (event.time() == seconds) refillChests(event);
        for (BorderEvent event : borders) if (event.time() == seconds) shrinkBorder(event);
    }

    private void runInstantEvents() {
        for (SupplyEvent event : itemDrops) if (event.time() == 0) givePlayers(event);
        for (SupplyEvent event : chestFills) if (event.time() == 0) refillChests(event);
    }

    private void givePlayers(SupplyEvent event) {
        Collection<ItemStack> sharedItems = event.sharedItems(game);
        if (sharedItems.isEmpty()) return;
        List<ItemStack> announcementItems = cloneItems(sharedItems);
        for (GamePlayer player : game.playerManager().getPlayers()) game.data().give(player, cloneItems(sharedItems));
        if (event.time() > 0) {
            announceSupply("Gave players ", announcementItems);
            game.uiManager().sound(game.playerManager().getPlayers(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        }
    }

    private void refillChests(SupplyEvent event) {
        int multiplier = Math.max(1, game.config().getInt("team_size"));
        for (GameChest chest : game.chests()) {
            chest.ensurePresent();
            for (int i = 0; i < multiplier; i++) chest.addItems(event.items(null, game));
        }
        if (event.time() > 0) {
            game.uiManager().subtitle(game.playerManager().getPlayers(), Component.text().append(Component.text("! ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)).append(Component.text("Chests Refilled", NamedTextColor.GREEN)).append(Component.text(" !", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)).build());
            game.uiManager().sound(game.playerManager().getPlayers(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.05f);
        }
    }

    private void shrinkBorder(BorderEvent event) {
        GameBorder border = game.mainBorder();
        if (border == null) {
            return;
        }
        Vector dimensions = border.dimensions();
        Vector target = dimensions.clone().multiply(event.scale());
        double change = Math.abs(dimensions.getX() - target.getX());
        if (borderBlocksPerTick < 0) {
            borderBlocksPerTick = event.time() <= 0 ? change : change / (event.time() * 20.0);
        }
        int ticks = borderBlocksPerTick <= 0 || change <= 0 ? 0 : Math.max(1, (int) Math.round(change / borderBlocksPerTick));
        border.setDimensions(target, ticks);
        game.uiManager().subtitle(game.playerManager().getPlayers(), Component.text()
            .append(Component.text("! ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("Border Shrinking", NamedTextColor.RED))
            .append(Component.text(" !", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        .build());
        game.uiManager().sound(game.playerManager().getPlayers(), Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.75f);
    }

    private void announceSupply(String prefix, List<ItemStack> items) {
        for (ItemStack item : items) game.uiManager().gameMessage(game.playerManager().getPlayers(), Component.text(prefix).append(game.data().itemName(item)).append(Component.text(" x" + item.getAmount())));
    }

    private void filterExplosion(List<Block> blocks) {
        blocks.removeIf(block -> !mutable(block.getLocation()));
    }

    private boolean mutable(org.bukkit.Location location) {
        return game.roundActive() && game.world().posInRegion(GameLocation.fromBukkit(location), "mutable_zone");
    }

    private TimedLabel nextTimedEvent(int seconds) {
        TimedLabel next = null;
        for (SupplyEvent event : itemDrops) if (event.time() > seconds) next = earliest(next, new TimedLabel("Item Drop", event.time(), 2));
        for (SupplyEvent event : chestFills) if (event.time() > seconds) next = earliest(next, new TimedLabel("Chest Refill", event.time(), 1));
        for (BorderEvent event : borders) if (event.time() > seconds) next = earliest(next, new TimedLabel("Border Shrink", event.time(), 0));
        return next;
    }

    private TimedLabel earliest(TimedLabel current, TimedLabel candidate) {
        return current == null || candidate.time() < current.time() || candidate.time() == current.time() && candidate.priority() < current.priority() ? candidate : current;
    }

    private SupplyEvent readSupply(Map<?, ?> entry) {
        return new SupplyEvent(number(entry.get("time")), strings(entry.get("pool")), string(entry.get("loot_table")));
    }

    private List<ItemStack> cloneItems(Collection<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>(items.size());
        for (ItemStack item : items) clones.add(item.clone());
        return clones;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> strings = new ArrayList<>(list.size());
        for (Object entry : list) if (entry != null) strings.add(String.valueOf(entry));
        return strings;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 1.0;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record BorderEvent(int time, double scale) {}

    public record SidebarEvent(String label, int remainingSeconds) {}

    private record TimedLabel(String label, int time, int priority) {}

    private record SupplyEvent(int time, List<String> pool, String lootTable) {
        Collection<ItemStack> sharedItems(VoidWars game) {
            return pool.isEmpty() ? items(null, game) : List.of(game.data().randomPool(pool));
        }

        Collection<ItemStack> items(GamePlayer player, VoidWars game) {
            if (!pool.isEmpty()) return List.of(game.data().randomPool(pool));
            return lootTable == null ? List.of() : game.data().loot(lootTable, player);
        }
    }
}
