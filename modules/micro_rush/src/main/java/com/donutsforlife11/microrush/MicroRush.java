package com.donutsforlife11.microrush;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.*;
import com.donutsforlife11.donutgame.api.map.*;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.ui.*;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.microrush.InventoryLayouts;
import com.donutsforlife11.microrush.microgames.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MicroRush extends GameModule {
    private final List<String> rotation = new ArrayList<>();
    private final List<String> enabled = new ArrayList<>();
    private final List<GameTeam> teams = new ArrayList<>();
    private final int[] points = new int[2], wins = new int[2];
    private final Map<String, InventoryLayouts> layouts = new HashMap<>();
    private final Set<UUID> disconnected = new HashSet<>();
    private GameMap lobby, arena;
    private Microgame current;
    private String selected;
    private boolean playing, ending;
    private boolean soloTesting;
    private boolean overtime;
    private com.donutsforlife11.donutgame.api.time.GameTimer microClock;
    private com.donutsforlife11.donutgame.api.ui.GameBossbar timerBar;
    private int timeLeft;
    private int generation;
    private org.bukkit.configuration.file.YamlConfiguration kits;

    @Override public void onLoad() {
        timerBar = uiManager().newBossbar();
        lobby = mapManager().map();
        // TODO: Review text and string identifiers below before release.
        if (lobby.getPoint("spawn") == null || lobby.getPoint("arena_origin") == null)
            // TODO: Review text and string identifiers below before release.
            throw new IllegalStateException("Micro Rush lobby needs spawn and arena_origin points.");
        // TODO: Review text and string identifiers below before release.
        world().setSpawnLocation(lobby.getPoint("spawn"));
        world().setDifficulty(org.bukkit.Difficulty.EASY);
        world().setHungerEnabled(false);
        world().gamerules().pvp(false);
        world().gamerules().fallDamage(false);
        world().gamerules().immediateRespawn(true);
        world().gamerules().showDeathMessages(false);
        world().gamerules().spawnMobs(false);
        world().gamerules().mobGriefing(false);
        world().gamerules().blockDrops(false);
        // TODO: Review text and string identifiers below before release.
        kits = data().config("kits.yml");
        // TODO: Review text and string identifiers below before release.
        for (String id : new LinkedHashSet<>(config().getStringList("active_games"))) {
            // TODO: Review text and string identifiers below before release.
            if (!Set.of("slime_shot","king_kong_kombo","crystal_clash","terror_tower","sniper_showdown","boom_ball").contains(id)) {
                logWarning("Skipping unknown Micro Rush microgame " + id);
                continue;
            }
            // TODO: Review text and string identifiers below before release.
            List<String> maps = config().getStringList("microgames." + id + ".maps");
            // TODO: Review text and string identifiers below before release.
            List<String> available = maps.stream().filter(map -> lobby.getSubmap(map) != null).toList();
            if (available.size() != maps.size()) logWarning("Ignoring missing submaps for " + id + ": " + maps);
            if (available.isEmpty()) {
                logWarning("Skipping " + id + ": no available schematic submaps.");
                continue;
            }
            config().set("microgames." + id + ".maps", available);
            enabled.add(id);
            layouts.put(id, new InventoryLayouts(this, id));
        }
        // TODO: Review text and string identifiers below before release.
        if (enabled.isEmpty()) throw new IllegalArgumentException("Micro Rush needs an active microgame.");
        // TODO: Review text and string identifiers below before release.
        teams.add(teamManager().newTeam().setColor(NamedTextColor.RED).setDisplayName(Component.text("Red", NamedTextColor.RED)));
        // TODO: Review text and string identifiers below before release.
        teams.add(teamManager().newTeam().setColor(NamedTextColor.BLUE).setDisplayName(Component.text("Blue", NamedTextColor.BLUE)));
        var players = new ArrayList<>(playerManager().getNonSpectators());
        // TODO: Review text and string identifiers below before release.
        soloTesting = players.size() < 2;
        if (soloTesting) logWarning("Solo practice: empty opposing teams will not end the game.");
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % 2).addPlayer(players.get(i));
            lobbyPlayer(players.get(i));
        }
        var sidebar = uiManager().newSidebar().setRefreshInterval(10)
            .addEntry(SidebarEntry.component("Microgame", () -> Component.text(current == null ? "Choosing round..." : current.name(), NamedTextColor.GREEN)));
        for(int i=0;i<2;i++) {
            final int team=i;
            sidebar.addEntry(SidebarEntry.component(team==0 ? "Red" : "Blue", () -> Component.text(wins[team], team==0 ? NamedTextColor.RED : NamedTextColor.BLUE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(" ("+points[team]+")",NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,false).decorate(net.kyori.adventure.text.format.TextDecoration.ITALIC))));
        }
        sidebar.addEntry(SidebarEntry.time("Time", () -> timeLeft*20))
            .addEntry(SidebarEntry.fraction("Alive Players", () -> playerManager().getNonSpectators().size(), () -> playerManager().getPlayers().size())).show();
    }

    @Override public void onStart() { chooseGame(); }

    private void chooseGame() {
        if (ending) return;
        playing = false;
        current = null;
        playerManager().getPlayers().forEach(p -> { p.cancelRespawn(); p.setSpectator(true,p.getLocation()); });
        if (rotation.isEmpty()) rotation.addAll(enabled);
        // TODO: Each active game appears once per rotation; long matches can reuse the full rotation after exhaustion.
        selected = rotation.remove(ThreadLocalRandom.current().nextInt(rotation.size()));
        Arrays.fill(points, 0);
        if (microClock != null) microClock.cancel();
        microClock = null;
        overtime = false;
        // TODO: Review text and string identifiers below before release.
        timeLeft = Math.max(1, config().getInt("max_round_time",150));
        int token = ++generation;
        timeManager().newTimer(60).onTick(2, timer -> {
            if (token != generation || ending) return;
            // TODO: Review text and string identifiers below before release.
            if (timer.elapsedTicks() == 0) uiManager().title(playerManager().getPlayers(), Component.text("Choosing round...", NamedTextColor.BLUE));
            uiManager().subtitle(playerManager().getPlayers(), Component.text(displayName(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())))));
            uiManager().playSound(playerManager().getPlayers(), GameSound.of(Sound.UI_BUTTON_CLICK));
        }).onFinish(ignored -> {
            if (token != generation || ending) return;
            // TODO: Review text and string identifiers below before release.
            uiManager().playSound(playerManager().getPlayers(),GameSound.of(Sound.ENTITY_PLAYER_LEVELUP));
            uiManager().title(playerManager().getPlayers(), Component.text("Round Chosen!", NamedTextColor.GREEN));
            uiManager().subtitle(playerManager().getPlayers(), Component.text(displayName(selected)));
            timeManager().newTimer(30).onFinish(delay -> loadPoint()).start();
        }).start();
    }

    private void loadPoint() {
        if (ending) return;
        playing = false;
        int token = ++generation;
        if (current != null) { current.stop(); current = null; }
        for (GamePlayer player : playerManager().getPlayers()) { player.cancelRespawn(); player.setSpectator(true,player.getLocation()); }
        world().gamerules().pvp(false);
        // TODO: Schematic regions are isolated from the lobby; erase the previous placement before pasting.
        clearCages();
        if (arena != null) {
            // TODO: Review text and string identifiers below before release.
            world().fill(arena.getRegion("arena"), Material.AIR);
            arena.getPoints().forEach((name, locations) -> locations.forEach(point -> world().removePoint(name, point)));
            arena.getRegions().forEach((name, regions) -> regions.forEach(region -> world().removeRegion(name, region)));
            arena = null;
        }
        // TODO: Review text and string identifiers below before release.
        List<String> maps = config().getStringList("microgames." + selected + ".maps");
        GameMap template = lobby.getSubmap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        // TODO: Review text and string identifiers below before release.
        mapManager().place(template, lobby.getPoint("arena_origin"), MapRotation.DEG_0).thenAccept(placed -> {
            if (ending || token != generation || !acceptsLiveOperations()) return;
            arena = placed;
            // TODO: Review text and string identifiers below before release.
            if (arena.getRegion("arena") == null || arena.getPoint("red_spawn") == null || arena.getPoint("blue_spawn") == null)
                // TODO: Review text and string identifiers below before release.
                throw new IllegalArgumentException("Microgame submap needs arena region, red_spawn point, and blue_spawn point.");
            // TODO: Review text and string identifiers below before release.
            if (arena.getRegion("arena").contains(lobby.getPoint("spawn"))) throw new IllegalArgumentException("Microgame arena overlaps the lobby spawn.");
            current = create(selected, arena);
            current.validate();
            for (GamePlayer player : playerManager().getOnlinePlayers()) if (team(player) >= 0) current.setupPlayer(player);
            // TODO: Review text and string identifiers below before release.
            buildCages();
            int ticks = Math.max(0, config().getInt("round_countdown", 3)) * 20 + 20;
            timeManager().newTimer(ticks).onTick(20, timer -> {
                if (token != generation || ending || timer.elapsedTicks() < 20 || timer.remainingTicks() <= 0) return;
                uiManager().playSound(playerManager().getPlayers(), GameSound.of(Sound.BLOCK_NOTE_BLOCK_PLING));
                uiManager().title(playerManager().getPlayers(), Component.text((timer.remainingTicks()+19)/20, timer.remainingTicks() > 40 ? NamedTextColor.RED : timer.remainingTicks() > 20 ? NamedTextColor.YELLOW : NamedTextColor.GREEN).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            }).onFinish(ignored -> {
                if (token != generation || ending) return;
                clearCages();
                for(var p : playerManager().getNonSpectators()) p.setGameMode(GameMode.SURVIVAL);
                playing = true;
                world().gamerules().pvp(true);
                // TODO: Review text and string identifiers below before release.
                uiManager().title(playerManager().getPlayers(), Component.text("> START <", NamedTextColor.WHITE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                uiManager().playSound(playerManager().getPlayers(), GameSound.of(Sound.ENTITY_ARROW_HIT_PLAYER));
                uiManager().playSound(playerManager().getPlayers(), GameSound.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(2f));
                current.start();
                if (microClock == null) microClock = timeManager().newTimer().onTick(20, tick -> {
                    if (ending || !playing) return;
                    if (tick.elapsedTicks() > 0 && --timeLeft <= 0) {
                        if (!overtime && points[0] == points[1] && (current == null || current.beginOvertime())) {
                            overtime = true;
                            // TODO: Review text and string identifiers below before release.
                            timeLeft = Math.max(1,config().getInt("overtime_time",60));
                        } else { completeMicrogame(points[0] == points[1] ? -1 : points[0] > points[1] ? 0 : 1); return; }
                    }
                    // TODO: Review text and string identifiers below before release.
                    refreshTimerBar();
                }).start();
                timeManager().newTimer(60).onFinish(save -> {
                    if (token == generation && playing && !ending) layouts.get(selected).saveAll();
                }).start();
            }).start();
        }).exceptionally(failure -> {
            if (!ending && acceptsLiveOperations()) {
                // TODO: Review text and string identifiers below before release.
                logError("Could not load Micro Rush arena.", failure);
                ending = true;
                unload();
            }
            return null;
        });
    }

    private void refreshTimerBar() {
                    timerBar.setColor(net.kyori.adventure.bossbar.BossBar.Color.WHITE).setTitle(Component.text(points[0], NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(" | ", NamedTextColor.GRAY)).append(Component.text(String.format("%02d:%02d", timeLeft/60, timeLeft%60), NamedTextColor.WHITE)).append(Component.text(" | ", NamedTextColor.GRAY)).append(Component.text(points[1], NamedTextColor.BLUE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)))
                        // TODO: Review text and string identifiers below before release.
                        .setProgress(timeLeft / (float)Math.max(1,config().getInt(overtime ? "overtime_time" : "max_round_time",overtime ? 60 : 150)))
                        .show(playerManager().getOnlinePlayers());
    }

    public void score(int team, boolean resetPoint) {
        // TODO: Every microgame uses the same point target. Crystal Clash keeps its arena until a team reaches it.
        if (!playing || ending || team < 0 || team > 1) return;
        points[team]++;
        refreshTimerBar();
        for (GamePlayer player : playerManager().getPlayers()) {
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(player, Component.text(team(player) == team ? "+ 1 Team scored point! 1 +" : "Enemy scored point!",
                team(player) == team ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        // TODO: Review text and string identifiers below before release.
        uiManager().gameMessage(playerManager().getPlayers(), teams.get(team).displayName().append(Component.text(" team has scored!")));
        world().particle(Particle.FIREWORK, current.spawn(team), 40, 0.5, 0.5, 0.5, 0.1, null);
        if (points[team] >= winTarget() || overtime) {
            playing = false;
            generation++;
            if (microClock != null) microClock.cancel();
            timerBar.hide();
            current.stop();
            wins[team]++;
            for (GamePlayer player : playerManager().getPlayers()) {
                player.cancelRespawn();
                player.setSpectator(true, player.getLocation());
                // TODO: Review text and string identifiers below before release.
                uiManager().title(player, Component.text(team(player) == team ? "Round Won" : "Round Lost"));
            }
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(playerManager().getPlayers(), teams.get(team).displayName().append(Component.text(" team wins round!")));
            if (wins[team] >= winTarget()) finish(team);
            else timeManager().newTimer(60).onFinish(ignored -> chooseGame()).start();
        } else if (resetPoint) restartPoint();
    }

    private void completeMicrogame(int winner) {
        if (microClock != null) microClock.cancel();
        timerBar.hide();
        if (winner >= 0) {
            playing = false;
            generation++;
            if (current != null) current.stop();
            wins[winner]++;
            // TODO: Rewrite timed microgame result text.
            uiManager().title(playerManager().getPlayers(),teams.get(winner).displayName().append(Component.text(" wins the microgame!")));
            if (wins[winner] >= winTarget()) finish(winner);
            else timeManager().newTimer(60).onFinish(ignored -> chooseGame()).start();
            return;
        }
        playing = false;
        generation++;
        if (current != null) current.stop();
        // TODO: Review text and string identifiers below before release.
        uiManager().title(playerManager().getPlayers(),Component.text("Tie!"));
        timeManager().newTimer(60).onFinish(ignored -> chooseGame()).start();
    }

    public void restartPoint() {
        if (!playing || ending) return;
        playing = false;
        generation++;
        current.stop();
        playerManager().getPlayers().forEach(p -> { p.cancelRespawn(); p.setSpectator(true, p.getLocation()); });
        int token = generation;
        timeManager().newTimer(80).onFinish(ignored -> { if (token == generation) loadPoint(); }).start();
    }

    private void finish(int winner) {
        if (ending) return;
        ending = true;
        if (microClock != null) microClock.cancel();
        timerBar.hide();
        playing = false;
        generation++;
        if (current != null) current.stop();
        world().gamerules().pvp(false);
        for (GamePlayer player : playerManager().getPlayers()) {
            player.cancelRespawn();
            // TODO: Review text and string identifiers below before release.
            uiManager().title(player, Component.text(team(player) == winner ? "VICTORY" : "DEFEAT", team(player) == winner ? NamedTextColor.GOLD : NamedTextColor.RED));
        }
        // TODO: Review text and string identifiers below before release.
        uiManager().subtitle(playerManager().getPlayers(), winner < 0 ? Component.text("No teams remain.") : teams.get(winner).displayName().append(Component.text(" team wins!")));
        timeManager().newTimer(240).onFinish(ignored -> unload()).start();
    }

    private final Map<GameLocation, org.bukkit.block.data.BlockData> cageBlocks = new HashMap<>();
    private void clearCages() {
        cageBlocks.forEach((at, block) -> world().setBlock(at, block));
        cageBlocks.clear();
    }
    private void buildCages() {
        clearCages();
        for(var p : playerManager().getNonSpectators()) p.setGameMode(GameMode.ADVENTURE);
        var red = arena.getPoint("red_spawn"); var blue = arena.getPoint("blue_spawn");
        boolean shared = red.blockX() == blue.blockX() && red.blockY() == blue.blockY() && red.blockZ() == blue.blockZ();
        for (int team = 0; team < 2; team++) {
            var spawn = team == 0 ? red : blue;
            int rx = Math.abs(red.x()-blue.x()) > Math.abs(red.z()-blue.z()) ? 1 : 5;
            int rz = rx == 1 ? 5 : 1;
            for (int x=-rx; x<=rx; x++) for(int z=-rz; z<=rz; z++) for(int y=0;y<=2;y++) {
                if(Math.abs(x)!=rx && Math.abs(z)!=rz && y!=2) continue;
                var at = new GameLocation(spawn.world(),spawn.blockX()+x,spawn.blockY()+y,spawn.blockZ()+z);
                var block = world().blockAt(at);
                if(cageBlocks.containsKey(at)) { block.setType(Material.WHITE_STAINED_GLASS); continue; }
                if(!block.getType().isAir()) continue;
                cageBlocks.put(at,block.getBlockData());
                block.setType(shared ? Material.WHITE_STAINED_GLASS : team==0 ? Material.RED_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
            }
        }
    }

    private void lobbyPlayer(GamePlayer player) {
        // TODO: Review text and string identifiers below before release.
        player.setSpawnPoint(lobby.getPoint("spawn"));
        // TODO: Review text and string identifiers below before release.
        player.setSpectator(team(player) < 0, lobby.getPoint("spawn"));
        if (team(player) >= 0) player.setGameMode(GameMode.ADVENTURE);
    }

    private void joinPlayer(GamePlayer player) {
        disconnected.remove(player.uuid());
        // TODO: Review text and string identifiers below before release.
        if (ending || team(player) < 0 && !config().getBoolean("team_new_players", true)) { lobbyPlayer(player); return; }
        if (team(player) < 0) {
            GameTeam fairest = teams.stream().min(Comparator.comparingLong(t -> t.getPlayers().stream().filter(GamePlayer::isOnline).count())).orElseThrow();
            fairest.addPlayer(player);
        }
        if (playing && current != null) current.lateJoin(player); else lobbyPlayer(player);
    }

    @GameEventHandler public void lateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        // TODO: Review text and string identifiers below before release.
        event.setSpectatorLocation(lobby.getPoint("spawn"));
        // TODO: Review text and string identifiers below before release.
        if (!ending && config().getBoolean("team_new_players", true)) {
            joinPlayer(event.getPlayer());
            event.setJoinAsSpectator(event.getPlayer().isSpectator());
        }
    }
    @GameEventHandler public void quit(GameEvent<PlayerQuitEvent> event) {
        leavePlayer(event.getPlayer());
    }
    @GameEventHandler public void leave(GameEvent<GamePlayerLeaveEvent> event) {
        leavePlayer(event.getPlayer());
    }
    private void leavePlayer(GamePlayer player) {
        disconnected.add(player.uuid());
        player.cancelRespawn();
        player.setSpectator(true);
        if (playing && current != null) current.quit(player);
        var present = java.util.stream.IntStream.range(0,2).filter(i -> teams.get(i).getPlayers().stream().anyMatch(p -> p.isOnline() && !disconnected.contains(p.uuid()))).boxed().toList();
        if (hasStarted() && (present.isEmpty() || !soloTesting && present.size() == 1)) finish(present.isEmpty() ? -1 : present.getFirst());
    }
    @GameEventHandler public void death(GameEvent<PlayerDeathEvent> event) {
        event.clearDrops(); event.setKeepInventory(true); event.setDroppedExp(0);
        GamePlayer player = event.getPlayer();
        player.setSpectator(true, player.spawnPoint());
        if (playing && current != null) current.death(player);
    }
    @GameEventHandler public void move(GameEvent<PlayerMoveEvent> event) {
        if (playing && current != null && !event.getPlayer().isSpectator() && event.hasChangedPosition()) current.move(event);
    }
    @GameEventHandler public void breakBlock(GameEvent<BlockBreakEvent> event) {
        if (!playing || current == null) event.setCancelled(true); else current.breakBlock(event);
    }
    @GameEventHandler public void placeBlock(GameEvent<BlockPlaceEvent> event) {
        if (!playing || current == null) event.setCancelled(true); else current.placeBlock(event);
    }
    @GameEventHandler public void damage(GameEvent<EntityDamageEvent> event) {
        if (!playing || current == null) event.setCancelled(true); else current.damage(event);
    }
    @GameEventHandler public void hit(GameEvent<PrePlayerAttackEntityEvent> event) {
        if (playing && current != null && !event.getPlayer().isSpectator()) current.attack(event);
    }
    @GameEventHandler public void projectile(GameEvent<ProjectileHitEvent> event) {
        if (playing && current != null) current.projectile(event);
    }
    @GameEventHandler public void launch(GameEvent<ProjectileLaunchEvent> event) {
        if (current != null && event.getEntity() instanceof GameEntity entity) current.track(entity);
        if (playing && current != null) current.launched(event);
    }
    @GameEventHandler public void explosion(GameEvent<EntityExplodeEvent> event) { event.removeBlocksIf(block -> true); }
    @GameEventHandler public void blockExplosion(GameEvent<BlockExplodeEvent> event) { event.removeBlocksIf(block -> true); }

    private Microgame create(String id, GameMap map) {
        return switch (id) {
            // TODO: Review text and string identifiers below before release.
            case "slime_shot" -> new SlimeShot(this, map);
            // TODO: Review text and string identifiers below before release.
            case "king_kong_kombo" -> new KingKongKombo(this, map);
            // TODO: Review text and string identifiers below before release.
            case "crystal_clash" -> new CrystalClash(this, map);
            // TODO: Review text and string identifiers below before release.
            case "terror_tower" -> new TerrorTower(this, map);
            // TODO: Review text and string identifiers below before release.
            case "sniper_showdown" -> new SniperShowdown(this, map);
            // TODO: Review text and string identifiers below before release.
            case "boom_ball" -> new BoomBall(this, map);
            default -> throw new IllegalArgumentException(id);
        };
    }
    public int team(GamePlayer player) { return teams.indexOf(player.getTeam()); }
    public GameTeam team(int index) { return teams.get(index); }
    // TODO: Review text and string identifiers below before release.
    public boolean soloTesting() { return soloTesting; }
    public int winTarget() { return Math.max(1, config().getInt("wins_required", 3)); }
    public boolean playing(Microgame game) { return playing && !ending && current == game; }
    public void prepareLayout(GamePlayer player) { layouts.get(selected).prepare(player); }
    public org.bukkit.configuration.ConfigurationSection kit(String id) { return kits.getConfigurationSection(id); }
    public static String displayName(String id) {
        return switch (id) {
            // TODO: Review text and string identifiers below before release.
            case "slime_shot" -> "Slime Shot"; case "king_kong_kombo" -> "King Kong Kombo";
            // TODO: Review text and string identifiers below before release.
            case "crystal_clash" -> "Crystal Clash"; case "terror_tower" -> "Terror Tower";
            // TODO: Review text and string identifiers below before release.
            case "sniper_showdown" -> "Sniper Showdown"; case "boom_ball" -> "Boom Ball";
            default -> id;
        };
    }
}
