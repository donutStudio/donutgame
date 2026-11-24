package com.donutsforlife11.zonewars;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.*;
import com.donutsforlife11.donutgame.api.map.*;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.*;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.zonewars.InventoryLayouts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.bossbar.BossBar;

public final class ZoneWars extends GameModule {
    private final Map<GameTeam, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, GameTimer> captureProtection = new HashMap<>();
    private record CapturePoint(GameLocation location, GameEntity visual, GameEntity target) { }
    private final Map<GameLocation, CapturePoint> activePoints = new HashMap<>();
    private final Map<UUID, GameEntity> drops = new HashMap<>();
    private ZoneWarsPlayers players;
    private InventoryLayouts layouts;
    private GameBossbar bossbar;
    private GameSidebar sidebar;
    private GameTimer clock;
    private boolean running, ending, overtime;
    private boolean multiplierAnnounced, respawnsDisabled;
    private final Set<UUID> eliminated = new HashSet<>();
    private int remaining;

    @Override public void onLoad() {
        // RODO: Review text and string identifiers below before release.
        require(!spawns().isEmpty(), "spawn point(s)");
        // RODO: Review text and string identifiers below before release.
        require(borderRegion() != null, "border region");
        // RODO: Review text and string identifiers below before release.
        require(!world().getPoints("capture_point").isEmpty(), "capture_point point(s)");
        // RODO: Review text and string identifiers below before release.
        require(config().getInt("item_count", 8) <= 0 || !world().getPoints("item_drop").isEmpty(), "item_drop point(s)");
        world().setSpawnLocation(spawns().getFirst());
        world().setDifficulty(org.bukkit.Difficulty.EASY);
        world().gamerules().pvp(false);
        world().gamerules().showDeathMessages(false);
        world().gamerules().blockDrops(false);
        world().gamerules().immediateRespawn(true);
        world().gamerules().spawnMobs(false);
        // RODO: Review text and string identifiers below before release.
        world().setHungerEnabled(config().getBoolean("hunger_enabled", false));
        // RODO: Review text and string identifiers below before release.
        remaining = Math.max(1, config().getInt("game_duration", 300));
        // RODO: Review text and string identifiers below before release.
        layouts = new InventoryLayouts(this, "kit");
        players = new ZoneWarsPlayers(this);
        players.loadPlayers();
        teamManager().getTeams().forEach(team -> { team.setFriendlyFire(false); scores.put(team, 0); });

        bossbar = uiManager().newBossbar().setColor(BossBar.Color.WHITE);
        sidebar = uiManager().newSidebar().setRefreshInterval(20);
        var listedTeams = new ArrayList<>(scores.keySet());
        sidebar.addEntry(SidebarEntry.time("Time", () -> remaining * 20));
        for (GameTeam team : listedTeams) {
            String label = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(team.displayName());
            sidebar.addEntry(SidebarEntry.integer(label, () -> scores.getOrDefault(team, 0)));
        }
        sidebar.addEntry(SidebarEntry.blank()).addEntry(SidebarEntry.integer("Kills", p -> kills.getOrDefault(p.uuid(), 0))).show();
        refreshBar();
        int count = Math.min(world().getPoints("capture_point").size(), Math.max(1, config().getInt("point_count", 2)));
        for (int i = 0; i < count; i++) generatePoint();
    }

    @Override public void onStart() {
        sidebar.hide().show();
        layouts.saveAll();
        players.startPlayers();
        running = true;
        world().gamerules().pvp(true);
        // RODO: Review text and string identifiers below before release.
        for (int i = 0; i < Math.max(0, config().getInt("item_count", 8)); i++) generateDrop();
        updateMilestones();
        clock = timeManager().newTimer().onTick(20, timer -> {
            if (!running || overtime || timer.elapsedTicks() == 0) return;
            remaining = Math.max(0, remaining - 1);
            if (remaining == 0) timeExpired();
            if (!running) return;
            updateMilestones();
            if (running) refreshBar();
        }).start();
    }

    private void updateMilestones() {
        if (!running) return;
        if (!multiplierAnnounced && remaining <= config().getInt("score_multiplier_time", 60)) {
            multiplierAnnounced = true;
            announce("x" + Math.max(1, config().getInt("score_multiplier", 2)) + " points!");
        }
        if (!respawnsDisabled && remaining <= config().getInt("no_respawn_time", 0)) {
            disableRespawns();
        }
    }

    private void disableRespawns() {
        if (respawnsDisabled) return;
        respawnsDisabled = true;
        boolean cancelledPendingRespawns = playerManager().getPlayers().stream().anyMatch(players::respawning);
        players.cancelRespawns();
        announce("No more respawns!");
        if (cancelledPendingRespawns) checkAlive(null);
    }

    private void announce(String text) {
        message(text);
        uiManager().title(playerManager().getPlayers(), Component.text(String.format("%02d:%02d left", remaining / 60, remaining % 60)));
        uiManager().subtitle(playerManager().getPlayers(), Component.text(text));
    }

    private void generatePoint() {
        if (ending || overtime) return;
        // RODO: Review text and string identifiers below before release.
        var candidates = new ArrayList<>(world().getPoints("capture_point").stream().map(p -> new GameLocation(p.world(), p.x(), p.y()+1, p.z(), p.yaw(), p.pitch())).toList());
        candidates.removeIf(point -> activePoints.keySet().stream().anyMatch(active -> sameBlock(active, point)));
        if (candidates.isEmpty()) return;
        GameLocation point = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // RODO: Review text and string identifiers below before release.
        float size = config().getInt("capture_mode", 0) == 0 ? 0.95f : 1.1f;
        double offset = (1-size)/2d;
        GameEntity visual = world().blockDisplay(new GameLocation(point.x()+offset,point.y()+offset,point.z()+offset),
            com.donutsforlife11.donutgame.api.object.GameBlocks.SEA_LANTERN,size);
        visual.setGlowing(true);
        GameEntity target = null;
        // RODO: Review text and string identifiers below before release.
        if (config().getInt("capture_mode", 0) == 0) world().setBlock(point, Material.BEACON);
        else {
            target = world().summon(center(point), GameEntities.SHULKER);
            target.setAI(false);
            target.setSilent(true);
            target.setAttributeBase(Attribute.MAX_HEALTH, 50);
            target.setHealth(50);
            target.setGlowing(true);
            target.setInvulnerable(false);
        }
        activePoints.put(point, new CapturePoint(point, visual, target));
    }

    private void removePoint(CapturePoint point) {
        activePoints.remove(point.location());
        world().setBlock(point.location(), Material.AIR);
        point.visual().remove();
        if (point.target() != null && !point.target().isDead()) point.target().remove();
    }

    private void capture(CapturePoint captured, GamePlayer player) {
        if (!running || ending || overtime || !activePoints.containsKey(captured.location())) return;
        removePoint(captured);
        timeManager().newTimer(200).onFinish(ignored -> generatePoint()).start();
        if (player == null || player.isSpectator() || player.getTeam() == null) return;
        GameLocation point = captured.location();
        // RODO: Review text and string identifiers below before release.
        int points = value("capture_score", 100);
        GameTimer previous = captureProtection.remove(player.uuid());
        if (previous != null) previous.cancel();
        captureProtection.put(player.uuid(), timeManager().newTimer(10).onFinish(ignored -> captureProtection.remove(player.uuid())).start());
        world().particle(Particle.FIREWORK, center(point), 40, 0.5, 0.5, 0.5, 0.15, null);
        uiManager().playSound(playerManager().getPlayers(), GameSound.of(Sound.ENTITY_FIREWORK_ROCKET_BLAST));
        uiManager().title(playerManager().getPlayers(), Component.empty());
        for (GamePlayer viewer : playerManager().getPlayers()) uiManager().subtitle(viewer,
            viewer.getTeam() == player.getTeam()
            ? Component.text("+ ",NamedTextColor.GRAY).append(Component.text(points)).append(Component.text(" Team captured point! ",NamedTextColor.GREEN)).append(Component.text(points)).append(Component.text(" +"))
            : Component.text("Team lost point!",NamedTextColor.RED));
        addScore(player.getTeam(), points);
    }

    private void generateDrop() {
        if (!running || ending || overtime) return;
        drops.values().removeIf(GameEntity::isDead);
        // RODO: Review text and string identifiers below before release.
        long occupied = drops.values().stream().map(drop -> drop.getLocation().blockX() + ":" + drop.getLocation().blockY() + ":" + drop.getLocation().blockZ()).distinct().count();
        // RODO: Review text and string identifiers below before release.
        if (occupied >= Math.max(0,config().getInt("item_count",8))) return;
        // RODO: Review text and string identifiers below before release.
        var available = new ArrayList<>(world().getPoints("item_drop").stream()
            .map(p -> new GameLocation(p.world(), p.x(), p.y() + 1, p.z(), p.yaw(), p.pitch())).toList());
        available.removeIf(point -> drops.values().stream().anyMatch(drop -> !drop.isDead() && distance(drop.getLocation(), point) < 0.1));
        if (available.isEmpty()) return;
        GameLocation point = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        // RODO: Review text and string identifiers below before release.
        for (var item : data().lootTable("zone_wars:items").roll()) {
            GameEntity drop = world().dropItem(point, item);
            drop.setVelocity(new org.bukkit.util.Vector());
            drop.setGravity(false);
            drop.setGlowing(true);
            drop.setInvulnerable(true);
            drops.put(drop.uuid(), drop);
        }
    }

    private int value(String key, int fallback) {
        // RODO: Review text and string identifiers below before release.
        int multiplier = remaining <= config().getInt("score_multiplier_time", 60) ? Math.max(1, config().getInt("score_multiplier", 2)) : 1;
        return config().getInt(key, fallback) * multiplier;
    }

    private void addScore(GameTeam team, int amount) {
        if (!running || ending) return;
        scores.put(team, Math.max(0, scores.getOrDefault(team, 0) + amount));
        refreshBar();
        sidebar.refresh();
        // RODO: Review text and string identifiers below before release.
        if (scores.get(team) >= Math.max(1, config().getInt("win_score", 1000))) finish(team);
    }

    private void timeExpired() {
        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var leaders = scores.keySet().stream().filter(team -> scores.get(team) == best).toList();
        if (leaders.size() == 1) { finish(leaders.getFirst()); return; }
        overtime = true;
        var border = borderManager().newBorder(borderRegion());
        // RODO: Review text and string identifiers below before release.
        border.setDimensions(new org.bukkit.util.Vector(1, border.dimensions().getY(), 1), Math.max(1, config().getInt("overtime_shrink_time", 90)) * 20);
        disableRespawns();
        if (!running) return;
        new ArrayList<>(activePoints.values()).forEach(this::removePoint);
        // TODO: Review text and string identifiers below before release.
        uiManager().title(playerManager().getPlayers(), Component.text("OVERTIME", NamedTextColor.RED));
        // TODO: Review text and string identifiers below before release.
        uiManager().subtitle(playerManager().getPlayers(), Component.text("Last team standing wins!"));
    }

    private void checkAlive(GamePlayer excluded) {
        if (!running || ending) return;
        var alive = scores.keySet().stream().filter(team -> team.getPlayers().stream()
            .anyMatch(p -> p != excluded && p.isOnline() && !eliminated.contains(p.uuid())
                && (!p.isSpectator() || !respawnsDisabled && players.respawning(p)))).toList();
        if (alive.size() <= 1) finish(alive.isEmpty() ? null : alive.getFirst());
    }

    private void finish(GameTeam winner) {
        if (ending) return;
        ending = true;
        running = false;
        if (clock != null) clock.cancel();
        players.cancelRespawns();
        world().gamerules().pvp(false);
        refreshBar();
        sidebar.refresh();
        for (GamePlayer player : playerManager().getPlayers()) {
            player.setSpectator(true);
            uiManager().title(player, Component.text(
            // RODO: Review text and string identifiers below before release.
            player.getTeam() == winner && winner != null ? "VICTORY" : "DEFEAT",
            player.getTeam() == winner ? NamedTextColor.GOLD : NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
        }
        // TODO: Review text and string identifiers below before release.
        uiManager().subtitle(playerManager().getPlayers(), winner == null ? Component.text("No teams remain.") : winner.displayName().append(Component.text(" wins!")));
        timeManager().newTimer(240).onFinish(ignored -> unload()).start();
    }

    private void refreshBar() {
        // RODO: Review text and string identifiers below before release.
        var teams = new ArrayList<>(scores.keySet());
        Component title = Component.empty();
        if (!teams.isEmpty()) title = title.append(Component.text(scores.get(teams.getFirst()), teams.getFirst().color()).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        title = title.append(Component.text(" | ", NamedTextColor.GRAY)).append(Component.text(overtime ? "OVERTIME" : String.format("%02d:%02d", remaining / 60, remaining % 60), NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, overtime)).append(Component.text(" | ", NamedTextColor.GRAY));
        if (teams.size() > 1) title = title.append(Component.text(scores.get(teams.get(1)), teams.get(1).color()).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        bossbar.setTitle(title)
            .setColor(overtime ? BossBar.Color.RED : BossBar.Color.WHITE)
            // RODO: Review text and string identifiers below before release.
            .setProgress(overtime ? 1 : remaining / (float) Math.max(1, config().getInt("game_duration", 300)))
            .show(playerManager().getOnlinePlayers());
    }

    @GameEventHandler(ignoreCancelled = true) public void blockBreak(GameEvent<BlockBreakEvent> event) {
        CapturePoint point = activePoints.values().stream()
            .filter(p -> p.target() == null && sameBlock(p.location(), event.getBlock().getLocation())).findFirst().orElse(null);
        if (point != null) {
            event.setCancelled(true);
            capture(point, event.getPlayer());
            return;
        }
        if (!running || players.inSpawn(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @GameEventHandler(ignoreCancelled = true) public void blockPlace(GameEvent<BlockPlaceEvent> event) {
        if (!running || players.inSpawn(event.getBlockPlaced().getLocation()) || activePoints.keySet().stream().anyMatch(p -> distance(p, event.getBlockPlaced().getLocation()) < 4)) event.setCancelled(true);
    }

    @GameEventHandler public void explosion(GameEvent<EntityExplodeEvent> event) {
        event.removeBlocksIf(block -> players.inSpawn(block.getLocation()) || activePoints.keySet().stream().anyMatch(p -> sameBlock(p, block.getLocation())));
    }

    @GameEventHandler public void blockExplosion(GameEvent<BlockExplodeEvent> event) {
        event.removeBlocksIf(block -> players.inSpawn(block.getLocation()) || activePoints.keySet().stream().anyMatch(p -> sameBlock(p, block.getLocation())));
    }

    @GameEventHandler public void damage(GameEvent<EntityDamageEvent> event) {
        if (!running) { event.setCancelled(true); return; }
        if (event.getEntity() instanceof GamePlayer player && (players.protectedSpawn(player) || captureProtection.containsKey(player.uuid()))) event.setCancelled(true);
    }

    @GameEventHandler(ignoreCancelled = true) public void attack(GameEvent<EntityDamageByEntityEvent> event) {
        GamePlayer attacker = event.getAttacker();
        if (attacker != null) players.removeProtection(attacker);
    }

    @GameEventHandler public void entityDeath(GameEvent<EntityDeathEvent> event) {
        CapturePoint point = activePoints.values().stream()
            .filter(p -> p.target() != null && p.target().uuid().equals(event.getEntity().uuid())).findFirst().orElse(null);
        if (point == null) return;
        event.clearDrops();
        event.setDroppedExp(0);
        capture(point, event.getKiller());
    }

    @GameEventHandler public void teleport(GameEvent<EntityTeleportEvent> event) {
        if (activePoints.values().stream().anyMatch(p -> p.target() != null && p.target().uuid().equals(event.getEntity().uuid()))) event.setCancelled(true);
    }

    @GameEventHandler public void death(GameEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.getPlayer();
        // RODO: Starting equipment vanishes; collected loot drops normally, then respawns receive a fresh arranged kit.
        // RODO: Review text and string identifiers below before release.
        event.removeDropsIf(item -> item.getData(com.donutsforlife11.donutgame.api.item.GameItemComponents.integer("layout_index")) != null);
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        GamePlayer killer = event.getKiller();
        if (running && killer != null && killer != player && killer.getTeam() != player.getTeam() && killer.getTeam() != null) {
            kills.merge(killer.uuid(), 1, Integer::sum);
            // RODO: Review text and string identifiers below before release.
            addScore(killer.getTeam(), value("kill_score", 25));
        }
        // RODO: Review text and string identifiers below before release.
        if (running && player.getTeam() != null) addScore(player.getTeam(), value("death_score", 0));
        player.setSpectator(true, player.spawnPoint());
        player.clearItems();
        // RODO: Review text and string identifiers below before release.
        if (!ending && !respawnsDisabled && player.getTeam() != null) {
            players.beginRespawn(player);
            // RODO: Review text and string identifiers below before release.
            player.respawn(running ? Math.max(0, config().getInt("respawn_delay", 10)) * 20 : 1, () -> {
                players.giveRespawnKit(player);
                players.protect(player);
                return player.spawnPoint();
            });
        }
        // RODO: Review text and string identifiers below before release.
        if (respawnsDisabled) {
            eliminated.add(player.uuid());
            players.cancelRespawn(player);
            checkAlive(player);
        }
    }

    @GameEventHandler(ignoreCancelled = true) public void pickup(GameEvent<EntityPickupItemEvent> event) {
        if (!(event.getEntity() instanceof GamePlayer player) || player.isSpectator()) return;
        if (event.getRemainingItems() > 0) return;
        if (drops.remove(event.getItemEntity().uuid()) != null) timeManager().newTimer(1).onFinish(ignored -> generateDrop()).start();
    }

    @GameEventHandler public void lateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        event.setSpectatorLocation(world().getSpawnLocation());
        // RODO: Review text and string identifiers below before release.
        if (ending || overtime || running && remaining <= config().getInt("no_respawn_time", 0)
            // RODO: Review text and string identifiers below before release.
            || event.getPlayer().getTeam() == null && !config().getBoolean("team_new_players", true)) return;
        eliminated.remove(event.getPlayer().uuid());
        event.setJoinAsSpectator(false);
        players.join(event.getPlayer());
    }


    private void disconnect(GamePlayer player) {
        players.cancelRespawn(player);
        player.setSpectator(true);
        eliminated.add(player.uuid());
        checkAlive(player);
    }

    @GameEventHandler public void quit(GameEvent<PlayerQuitEvent> event) { disconnect(event.getPlayer()); }
    @GameEventHandler public void leave(GameEvent<GamePlayerLeaveEvent> event) { disconnect(event.getPlayer()); }

    void prepareLayout(GamePlayer player) { layouts.prepare(player); }
    boolean running() { return running; }
    // RODO: Review text and string identifiers below before release.
    public int teamCount() { return Math.max(2, config().getInt("team_count", 2)); }
    // RODO: Review text and string identifiers below before release.
    public List<GameLocation> spawns() { return world().getPoints("spawn"); }
    // RODO: Review text and string identifiers below before release.
    public GameRegion borderRegion() { return world().getRegion("border"); }
    private void message(String text) { uiManager().gameMessage(playerManager().getPlayers(), Component.text(text)); }
    // TODO: Review text and string identifiers below before release.
    private void require(boolean valid, String object) { if (!valid) throw new IllegalStateException("Zone Wars map needs " + object + "."); }
    static GameLocation center(GameLocation p) { return new GameLocation(p.world(), p.blockX() + 0.5, p.y(), p.blockZ() + 0.5, p.yaw(), p.pitch()); }
    static boolean sameBlock(GameLocation a, GameLocation b) { return a.blockX() == b.blockX() && a.blockY() == b.blockY() && a.blockZ() == b.blockZ(); }
    static double distance(GameLocation a, GameLocation b) { return Math.pow(a.x()-b.x(), 2) + Math.pow(a.y()-b.y(), 2) + Math.pow(a.z()-b.z(), 2); }
}
