package com.donutsforlife11.voidwars;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItems;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.object.BlockSpec;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.SidebarEntry;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWars extends GameModule {
    private static final String SPAWN = "spawn";
    private static final String BORDER = "starting_border";
    private static final String MUTABLE = "mutable_zone";
    private static final String SPAWN_PLATFORM = "spawn_platform";
    private static final String CHEST = "chest";

    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Set<UUID> lateSpectators = new LinkedHashSet<>();
    private final List<GameLocation> chests = new ArrayList<>();
    private final List<TimedLoot> itemDrops = new ArrayList<>();
    private final List<TimedLoot> chestFills = new ArrayList<>();
    private final List<BorderShrink> borderShrinks = new ArrayList<>();

    private int maxRounds;
    private int teamSize;
    private int baseRespawnTicks;
    private int groundCollapseTicks;
    private int pvpTicks;
    private int minChests;
    private int maxChests;
    private boolean protectRegions;
    private int round;
    private boolean roundStarted;
    private boolean roundEnding;
    private GameTimer roundTimer;
    private GameBorder border;

    @Override
    public void onLoad() {
        readConfig();
        validateMap();
        loadEvents();
        assignTeams();
        loadRound();
        setupSidebar();
    }

    @Override
    public void onStart() {
        startRound();
    }

    @Override
    public void onReload() {
        loadRound();
    }

    private void readConfig() {
        maxRounds = Math.max(1, config().getInt("max_rounds", 3));
        teamSize = Math.max(1, config().getInt("team_size", 1));
        baseRespawnTicks = Math.max(0, config().getInt("base_respawn_time", 20)) * 20;
        groundCollapseTicks = Math.max(0, config().getInt("ground_collapse_time", 15)) * 20;
        pvpTicks = Math.max(0, config().getInt("pvp_enablement_time", 20)) * 20;
        minChests = Math.max(0, config().getInt("min_chests", 0));
        maxChests = Math.max(minChests, config().getInt("max_chests", minChests));
        protectRegions = config().getBoolean("protect_regions", true);
    }

    private void validateMap() {
        require(SPAWN, world().getPoint(SPAWN) != null);
        require(BORDER, world().getRegion(BORDER) != null);
        require(SPAWN_PLATFORM, !world().getRegions(SPAWN_PLATFORM).isEmpty());
        if (protectRegions) {
            require(MUTABLE, world().getRegion(MUTABLE) != null);
        }
        if (maxChests > 0) {
            require(CHEST, !world().getPoints(CHEST).isEmpty());
        }
    }

    private void require(String mapObject, boolean valid) {
        if (!valid) {
            throw new IllegalStateException("Void Wars map is missing required object '" + mapObject + "'.");
        }
    }

    private void loadEvents() {
        InputStream input = VoidWars.class.getClassLoader().getResourceAsStream("events.yml");
        if (input == null) {
            throw new IllegalStateException("Void Wars module is missing events.yml.");
        }
        YamlConfiguration events = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
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

    private LootTable loot(Map<?, ?> entry) {
        Object table = entry.get("loot_table");
        if (table instanceof String key && !key.isBlank()) {
            return data().lootTable(key);
        }
        Object pool = entry.get("pool");
        if (pool instanceof List<?> values) {
            return data().lootPool(values.stream().map(String::valueOf).toList());
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

    private void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(playerManager().getPlayers());
        Collections.shuffle(players);
        int teamCount = Math.max(1, (int) Math.ceil(players.size() / (double) teamSize));
        for (int i = 0; i < teamCount; i++) {
            teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teams.size()).addPlayer(players.get(i));
        }
    }

    private void setupSidebar() {
        SidebarEntry nextEvent = SidebarEntry.time("Next Event", this::nextEventTicks)
            .setLabel(this::nextEventLabel);
        var sidebar = uiManager().newSidebar()
            .addEntry(SidebarEntry.fraction("Round", () -> round, () -> maxRounds))
            .addEntry(SidebarEntry.blank())
            .addEntry(nextEvent)
            .addEntry(SidebarEntry.blank());
        if (teamSize > 1) {
            sidebar.addEntry(SidebarEntry.fraction("Alive Teams", () -> teamManager().getNonSpectatorTeams().size(), () -> teamManager().getTeams().size()));
        }
        sidebar
            .addEntry(SidebarEntry.fraction("Alive Players", () -> playerManager().getNonSpectators().size(), () -> playerManager().getPlayers().size()))
            .addEntry(SidebarEntry.blank())
            .addEntry(SidebarEntry.integer("Kills", player -> kills.getOrDefault(player.uuid(), 0)))
            .setRefreshInterval(10)
            .show();
    }

    private void loadRound() {
        loadRound(false);
    }

    private void loadRound(boolean preservePlayerLocations) {
        round++;
        roundStarted = false;
        roundEnding = false;
        chests.clear();
        world().setHungerEnabled(false);
        world().gamerules().fallDamage(false);
        world().gamerules().pvp(false);
        world().setWorldSpawn(world().getPoint(SPAWN));
        border = borderManager().newBorder(world().getRegion(BORDER));
        spawnChests();

        for (GamePlayer player : playerManager().getPlayers()) {
            setupPlayer(player, true, preservePlayerLocations);
            player.addEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
    }

    private void startRound() {
        roundStarted = true;
        world().gamerules().fallDamage(true);
        world().setHungerEnabled(true);
        for (GamePlayer player : playerManager().getPlayers()) {
            player.clearEffect(PotionEffectType.INVISIBILITY);
        }
        roundTimer = timeManager().newTimer()
            .onTick(20, this::tickRound)
            .start();
    }

    private void tickRound(GameTimer timer) {
        int elapsed = timer.elapsedTicks();
        if (elapsed == groundCollapseTicks) {
            collapseSpawn();
        }
        if (elapsed == pvpTicks) {
            world().gamerules().pvp(true);
            if (elapsed > 0) {
                sound(Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.75f);
                gameMessage(Component.text("PvP is now enabled!"));
            }
        }
        itemDrops.stream().filter(event -> event.ticks() == elapsed).forEach(event -> dropItems(event.lootTable()));
        chestFills.stream().filter(event -> event.ticks() == elapsed).forEach(event -> fillChests(event.lootTable()));
        borderShrinks.stream().filter(event -> event.ticks() == elapsed).forEach(event -> shrinkBorder(event.scale()));
    }

    private void collapseSpawn() {
        for (GameRegion region : world().getRegions(SPAWN_PLATFORM)) {
            world().fill(region, BlockSpec.of(Material.AIR));
            sound(Sound.ENTITY_WARDEN_DEATH, 1f, 0.5f);
        }
        if (roundTimer != null && roundTimer.elapsedTicks() > 0) {
            gameMessage(Component.text("The ground has collapsed!"));
        }
    }

    private void setupPlayer(GamePlayer player, boolean clearItems) {
        setupPlayer(player, clearItems, false);
    }

    private void setupPlayer(GamePlayer player, boolean clearItems, boolean preserveLocation) {
        GameLocation spawn = world().getPoint(SPAWN);
        GameLocation startLocation = preserveLocation && player.location() != null ? player.location() : spawn;
        if (!preserveLocation) {
            player.teleport(spawn);
        }
        player.setSpawnPoint(startLocation);
        player.setSpectatablePlayers(teamSize <= 1 ? () -> playerManager().getPlayers() : List::of);
        player.setSpectatableTeams(teamSize <= 1 ? List::of : () -> teamManager().getTeams());
        if (lateSpectators.contains(player.uuid()) || roundStarted) {
            player.setSpectator(true, startLocation);
            return;
        }
        player.setSpectator(false, startLocation);
        player.setGameMode(GameMode.SURVIVAL);
        player.heal();
        player.setHunger(20);
        player.setSaturation(20);
        player.setArrowsInBody(0);
        player.clearEffects();
        player.clearExperience();
        player.setLevel(99);
        player.setExp(0.99f);
        if (clearItems) {
            player.clearItems();
        }
    }

    private void spawnChests() {
        List<GameLocation> points = new ArrayList<>(world().getPoints(CHEST));
        Collections.shuffle(points);
        int count = points.isEmpty() ? 0 : Math.min(points.size(), ThreadLocalRandom.current().nextInt(minChests, maxChests + 1));
        for (int i = 0; i < count; i++) {
            GameLocation location = points.get(i);
            world().setBlock(location, BlockSpec.of(Material.CHEST));
            chests.add(location);
        }
    }

    private void fillChests(LootTable lootTable) {
        for (GameLocation location : chests) {
            Block block = location.bukkitLocation().getBlock();
            if (block.getType() != Material.CHEST) {
                world().setBlock(location, BlockSpec.of(Material.CHEST));
                block = location.bukkitLocation().getBlock();
            }
            if (block.getState() instanceof Chest chest) {
                chest.getBlockInventory().clear();
                chest.setLootTable(lootTable);
                chest.setSeed(ThreadLocalRandom.current().nextLong());
                chest.update(true);
            }
        }
        if (roundTimer != null && roundTimer.elapsedTicks() > 0) {
            subtitle(playerManager().getPlayers(), Component.empty()
                .append(Component.text("! ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
                .append(Component.text("Chests Refilled", NamedTextColor.GREEN))
                .append(Component.text(" !", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            );
            sound(Sound.BLOCK_CHEST_OPEN, 1f, 1.25f);
        }
    }

    private void dropItems(LootTable lootTable) {
        Collection<ItemStack> items = GameItems.fromLootTable(lootTable);
        for (GamePlayer player : playerManager().getPlayers()) {
            for (ItemStack item : items) {
                player.giveItem(GameItem.from(item));
            }
        }
        if (roundTimer != null && roundTimer.elapsedTicks() > 0 && !items.isEmpty()) {
            for (ItemStack item : items) {
                gameMessage(Component.text("Gave players ")
                    .append(displayName(item))
                    .append(Component.text(" x" + item.getAmount()))
                );
            }
            sound(Sound.ENTITY_ITEM_PICKUP, 1f, 0f);
        }
    }

    private void shrinkBorder(double scale) {
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
        subtitle(playerManager().getPlayers(), Component.empty()
            .append(Component.text("! ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("Border Shrinking", NamedTextColor.RED))
            .append(Component.text(" !", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        );
        sound(Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.75f);
    }

    private void endRound(Collection<GamePlayer> winners) {
        if (roundEnding || !roundStarted) {
            return;
        }
        roundEnding = true;
        roundStarted = false;
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (border != null) {
            border.remove();
            border = null;
        }
        timeManager().newTimer(round >= maxRounds ? 100 : 50).onFinish(timer -> {
            title(playerManager().getSpectators(), Component.text("Round Over!"));
            sound(playerManager().getSpectators(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.5f);
            title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            sound(winners, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.675f);
            if (!winners.isEmpty()) {
                String label = winners.size() == 1 ? "Winner: " : "Winners: ";
                subtitle(playerManager().getPlayers(), Component.text(label + winners.stream()
                    .map(player -> player.bukkitPlayer() == null ? player.uuid().toString() : player.bukkitPlayer().getName())
                    .collect(Collectors.joining(", "))));
            }
            for (GamePlayer winner : winners) {
                winner.setSpectator(true, winner.location());
            }
            if (round >= maxRounds) {
                timeManager().newTimer(100).onFinish(ignored -> {
                    title(playerManager().getPlayers(), Component.text("Game over!", NamedTextColor.WHITE, TextDecoration.BOLD));
                    timeManager().newTimer(40).onFinish(ignored2 -> unload()).start();
                }).start();
            } else {
                timeManager().newTimer(100).onFinish(ignored -> resetForNextRound()).start();
            }
        }).start();
    }

    private void resetForNextRound() {
        reload().exceptionally(throwable -> {
            logError("Failed to reset Void Wars for the next round.", throwable);
            return null;
        });
    }

    private boolean tryRespawn(GamePlayer player) {
        GameTeam team = player.getTeam();
        if (team == null) {
            return false;
        }
        int respawnTicks = baseRespawnTicks * Math.max(0, team.getPlayers().size() - 1);
        if (team.allSpectators()) {
            for (GamePlayer teammate : team.getPlayers()) {
                teammate.cancelRespawn();
            }
            title(team.getPlayers(), Component.text(teamSize == 1 ? "Eliminated!" : "Team Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
            return false;
        }
        player.respawn(respawnTicks, () -> {
            GamePlayer teammate = livingTeammate(player);
            return teammate == null ? player.spawnPoint() : teammate.location();
        });
        return true;
    }

    private GamePlayer livingTeammate(GamePlayer player) {
        GameTeam team = player.getTeam();
        if (team == null) {
            return null;
        }
        GamePlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        GameLocation location = player.location();
        for (GamePlayer teammate : team.getNonSpectators()) {
            if (teammate == player) {
                continue;
            }
            double distance = location == null || teammate.location() == null ? 0.0 : distanceSquared(location, teammate.location());
            if (distance < nearestDistance) {
                nearest = teammate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private double distanceSquared(GameLocation left, GameLocation right) {
        double x = left.x() - right.x();
        double y = left.y() - right.y();
        double z = left.z() - right.z();
        return x * x + y * y + z * z;
    }

    private void checkRoundOver() {
        if (!roundStarted) {
            return;
        }
        Collection<GameTeam> aliveTeams = teamManager().getNonSpectatorTeams();
        if (aliveTeams.size() <= 1) {
            endRound(aliveTeams.isEmpty() ? List.of() : aliveTeams.iterator().next().getPlayers());
        }
    }

    @GameEventHandler
    public void onDeath(GameEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null) {
            return;
        }
        Entity attacker = event.bukkitEvent().getDamageSource().getCausingEntity();
        if (attacker instanceof Player bukkitAttacker) {
            GamePlayer gameAttacker = playerManager().getPlayer(bukkitAttacker);
            if (gameAttacker != null && gameAttacker != player) {
                kills.merge(gameAttacker.uuid(), 1, Integer::sum);
            }
        }
        if (!roundStarted) {
            player.respawn();
            return;
        }
        boolean canRespawn = livingTeammate(player) != null;
        if (canRespawn) {
            event.bukkitEvent().setKeepInventory(true);
            event.bukkitEvent().setKeepLevel(true);
            event.bukkitEvent().setDroppedExp(0);
            event.bukkitEvent().getDrops().clear();
        }
        GameLocation spectatorLocation = border != null && player.location() != null && border.containsLocation(player.location())
            ? player.location()
            : world().getRegion(BORDER).center();
        player.setSpectator(true, spectatorLocation);
        tryRespawn(player);
        checkRoundOver();
    }

    @GameEventHandler
    public void onJoin(GameEvent<PlayerJoinEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null && !teamManager().playerHasTeam(player)) {
            lateSpectators.add(player.uuid());
            setupPlayer(player, false);
        }
        checkRoundOver();
    }

    @GameEventHandler
    public void onQuit(GameEvent<PlayerQuitEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null) {
            player.setSpectator(true);
        }
        checkRoundOver();
    }

    @GameEventHandler
    public void onMove(GameEvent<PlayerMoveEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        GameLocation to = event.get("to", GameLocation.class);
        if (player == null || to == null || player.isSpectator() || roundStarted || world().regionContainsPos(BORDER, to)) {
            return;
        }
        event.bukkitEvent().setTo(world().getPoint(SPAWN).bukkitLocation());
    }

    @GameEventHandler
    public void onBlockBreak(GameEvent<BlockBreakEvent> event) {
        if (protectedLocation(event.get("block", Block.class))) {
            event.bukkitEvent().setCancelled(true);
        }
    }

    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        if (protectedLocation(event.get("block", Block.class))) {
            event.bukkitEvent().setCancelled(true);
        }
    }

    @GameEventHandler
    public void onEntityExplode(GameEvent<EntityExplodeEvent> event) {
        event.bukkitEvent().blockList().removeIf(this::protectedLocation);
    }

    @GameEventHandler
    public void onBlockExplode(GameEvent<BlockExplodeEvent> event) {
        event.bukkitEvent().blockList().removeIf(this::protectedLocation);
    }

    private boolean protectedLocation(Block block) {
        return block != null && protectedLocation(new GameLocation(world(), block.getLocation()));
    }

    private boolean protectedLocation(GameLocation location) {
        if (!roundStarted && !roundEnding) {
            return true;
        }
        return protectRegions && !world().regionContainsPos(MUTABLE, location);
    }

    private int nextEventTicks() {
        int elapsed = roundTimer == null ? 0 : roundTimer.elapsedTicks();
        return allEventTicks().stream()
            .filter(ticks -> ticks > elapsed)
            .min(Integer::compareTo)
            .map(ticks -> ticks - elapsed)
            .orElse(0);
    }

    private String nextEventLabel() {
        int elapsed = roundTimer == null ? 0 : roundTimer.elapsedTicks();
        if (elapsed < groundCollapseTicks) return "Ground Collapse";
        if (elapsed < pvpTicks) return "PvP Enabling";
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

    private List<Integer> allEventTicks() {
        List<Integer> ticks = new ArrayList<>();
        ticks.add(groundCollapseTicks);
        ticks.add(pvpTicks);
        itemDrops.forEach(event -> ticks.add(event.ticks()));
        chestFills.forEach(event -> ticks.add(event.ticks()));
        borderShrinks.forEach(event -> ticks.add(event.ticks()));
        return ticks;
    }

    private void gameMessage(Component message) {
        for (GamePlayer player : playerManager().getPlayers()) {
            player.gameMessage(message);
        }
    }

    private void title(Collection<GamePlayer> players, Component title) {
        for (GamePlayer player : players) {
            player.title(title);
        }
    }

    private void subtitle(Collection<GamePlayer> players, Component subtitle) {
        for (GamePlayer player : players) {
            player.subtitle(subtitle);
        }
    }

    private void sound(Sound sound, float volume, float pitch) {
        sound(playerManager().getPlayers(), sound, volume, pitch);
    }

    private void sound(Collection<GamePlayer> players, Sound sound, float volume, float pitch) {
        for (GamePlayer player : players) {
            player.playSound(sound, volume, pitch);
        }
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
