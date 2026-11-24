package com.donutsforlife11.doppelganger;

import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.Team.OptionStatus;
import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.event.*;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.*;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Doppelganger extends GameModule {
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> sheriffs = new HashSet<>();
    private final Set<UUID> cooldowns = new HashSet<>();
    private final Set<UUID> disguiseCooldowns = new HashSet<>();
    private final Map<UUID, Integer> strikes = new HashMap<>();
    private DoppelgangerNpcManager npcs;
    private GameTimer clock;
    private boolean running, ending;
    private boolean soloTesting;
    private int kills, goal, remaining;

    @Override public void onLoad() {
        // TODO: Review text and string identifiers below before release.
        if (spawns().isEmpty()) throw new IllegalStateException("Doppelganger needs spawn points.");
        world().setSpawnLocation(spawns().getFirst());
        world().setHungerEnabled(false);
        world().gamerules().pvp(false);
        world().gamerules().fallDamage(false);
        world().gamerules().showDeathMessages(false);
        world().gamerules().immediateRespawn(true);
        world().gamerules().spawnMobs(false);
        var players = new ArrayList<>(playerManager().getNonSpectators());
        // TODO: Review text and string identifiers below before release.
        soloTesting = players.size() < 2;
        if (soloTesting) logWarning("Solo practice: opposing-role win checks are disabled.");
        Collections.shuffle(players);
        // TODO: Review text and string identifiers below before release.
        int sheriffCount = Math.clamp(config().getInt("sheriff_count", 1), 0, Math.max(0, players.size() == 1 ? 1 : players.size()-1));
        var citizens = teamManager().newTeam().setTeamGlow(false).setNametagVisibility(OptionStatus.NEVER)
            .setSeeFriendlyInvisibles(false).setCollisionRule(OptionStatus.NEVER).setFriendlyFire(true);
        for (int i = 0; i < players.size(); i++) {
            GamePlayer player = players.get(i);
            citizens.addPlayer(player);
            alive.add(player.uuid());
            if (i < sheriffCount) sheriffs.add(player.uuid());
            GameLocation spawn = spawns().get(i % spawns().size());
            player.setSpectator(false, spawn);
            player.setGameMode(GameMode.ADVENTURE);
            player.setSpawnPoint(spawn);
            player.clearItems();
            player.clearActivePotionEffects();
            player.heal();
            player.setInvisible(true);
        }
        npcs = new DoppelgangerNpcManager(this);
        npcs.createCrowd(players);
        // TODO: Review text and string identifiers below before release.
        goal = Math.clamp(config().getInt("kill_goal", Math.max(1, npcs.botCount()/2)), 1, Math.max(1,npcs.botCount()));
        // TODO: Review text and string identifiers below before release.
        remaining = Math.max(1, config().getInt("game_duration", 300));
        uiManager().newSidebar().setRefreshInterval(20)
            // TODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.integer("Townspeople", () -> npcs.botCount() + alive.size()))
            // TODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.time("Time", () -> remaining * 20))
            .addEntry(SidebarEntry.custom(player -> Component.text(isSheriff(player)
                // TODO: Review text and string identifiers below before release.
                ? "Strikes: " + strikes.getOrDefault(player.uuid(), 0) + "/" + maxStrikes()
                // TODO: Review text and string identifiers below before release.
                : "Kills: " + kills + "/" + goal, NamedTextColor.YELLOW))).show();
    }

    @Override public void onStart() {
        running = true;
        npcs.startWandering();
        for (GamePlayer player : playerManager().getNonSpectators()) {
            // TODO: Review text and string identifiers below before release.
            uiManager().title(player, Component.text(isSheriff(player) ? "SHERIFF" : "DOPPELGANGER", isSheriff(player) ? NamedTextColor.BLUE : NamedTextColor.RED));
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(player, Component.text(isSheriff(player) ? "Catch every Doppelganger. Watch your strikes!" : "Blend in. Kill townspeople. Right click to disguise!"));
        }
        clock = timeManager().newTimer().onTick(20, timer -> {
            if (timer.elapsedTicks() > 0 && --remaining <= 0) finish(true);
        }).start();
        checkWin();
    }

    void attack(GamePlayer attacker, GameNpc target) {
        if (!canAct(attacker) || cooldowns.contains(attacker.uuid())) return;
        GamePlayer owner = npcs.owner(target);
        if (owner == attacker) return;
        cooldowns.add(attacker.uuid());
        // TODO: Review text and string identifiers below before release.
        timeManager().newTimer(Math.max(1, config().getInt("attack_cooldown", 2)) * 20).onFinish(ignored -> cooldowns.remove(attacker.uuid())).start();
        if (isSheriff(attacker)) {
            if (owner != null && !isSheriff(owner)) {
                eliminate(owner);
                // TODO: Review text and string identifiers below before release.
                uiManager().gameMessage(attacker, Component.text("You caught a Doppelganger!", NamedTextColor.GREEN));
            } else {
                int used = strikes.merge(attacker.uuid(), 1, Integer::sum);
                // TODO: Review text and string identifiers below before release.
                uiManager().gameMessage(attacker, Component.text("Innocent! Strike " + used + "/" + maxStrikes(), NamedTextColor.RED));
                if (owner == null) npcs.remove(target);
                if (used >= maxStrikes()) eliminate(attacker);
            }
        } else if (owner == null) {
            npcs.remove(target);
            kills++;
            uiManager().playSound(attacker, GameSound.of(Sound.ENTITY_PLAYER_ATTACK_CRIT));
        } else if (isSheriff(owner)) {
            eliminate(owner);
        }
        checkWin();
    }

    void disguise(GamePlayer player, GameNpc target) {
        if (!canAct(player) || isSheriff(player) || npcs.owner(target) != null || disguiseCooldowns.contains(player.uuid())) return;
        GameNpc body = npcs.body(player);
        if (body == null) return;
        // TODO: Swapping skins with a bot keeps each participant's appearance equally represented.
        var previous = body.profile();
        body.setProfile(target.profile());
        target.setProfile(previous);
        disguiseCooldowns.add(player.uuid());
        // TODO: Review text and string identifiers below before release.
        timeManager().newTimer(Math.max(1, config().getInt("disguise_cooldown", 10)) * 20).onFinish(ignored -> disguiseCooldowns.remove(player.uuid())).start();
        // TODO: Review text and string identifiers below before release.
        uiManager().gameMessage(player, Component.text("Disguise changed.", NamedTextColor.GREEN));
    }

    private boolean canAct(GamePlayer player) { return running && !ending && alive.contains(player.uuid()) && player.isOnline() && !player.isSpectator(); }

    private void eliminate(GamePlayer player) {
        if (!alive.remove(player.uuid())) return;
        npcs.removeBody(player);
        player.setSpectator(true, player.getLocation());
        // TODO: Review text and string identifiers below before release.
        uiManager().title(player, Component.text("Eliminated!", NamedTextColor.RED));
    }

    private void checkWin() {
        // TODO: Solo sessions can exercise NPCs without an absent opposing role immediately winning.
        if (soloTesting) {
            if (running && !ending && (kills >= goal || alive.isEmpty())) finish(false);
            return;
        }
        if (!running || ending) return;
        if (alive.stream().noneMatch(id -> !sheriffs.contains(id))) finish(true);
        else if (kills >= goal || alive.stream().noneMatch(sheriffs::contains)) finish(false);
    }

    private void finish(boolean sheriffsWon) {
        if (ending) return;
        running = false;
        ending = true;
        if (clock != null) clock.cancel();
        npcs.stop();
        for (GamePlayer player : playerManager().getPlayers()) {
            // TODO: Review text and string identifiers below before release.
            uiManager().title(player, Component.text(isSheriff(player) == sheriffsWon ? "VICTORY" : "DEFEAT",
                isSheriff(player) == sheriffsWon ? NamedTextColor.GOLD : NamedTextColor.DARK_RED));
            // TODO: Review text and string identifiers below before release.
            uiManager().subtitle(player, Component.text(sheriffsWon ? "Sheriffs caught the Doppelgangers!" : "Doppelgangers overran the town!"));
        }
        timeManager().newTimer(120).onFinish(ignored -> unload()).start();
    }

    @GameEventHandler public void move(GameEvent<PlayerMoveEvent> event) {
        if (alive.contains(event.getPlayer().uuid())) npcs.moveBody(event.getPlayer(), event.getTo());
    }
    @GameEventHandler public void damage(GameEvent<EntityDamageEvent> event) { event.setCancelled(true); }
    @GameEventHandler public void attackPlayer(GameEvent<io.papermc.paper.event.player.PrePlayerAttackEntityEvent> event) {
        if (event.getAttacked() instanceof GamePlayer target) {
            event.setCancelled(true);
            GameNpc body = npcs.body(target);
            if (body != null) attack(event.getPlayer(), body);
        }
    }
    @GameEventHandler public void breakBlock(GameEvent<BlockBreakEvent> event) { event.setCancelled(true); }
    @GameEventHandler public void placeBlock(GameEvent<BlockPlaceEvent> event) { event.setCancelled(true); }
    @GameEventHandler public void lateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        event.setJoinAsSpectator(true);
        event.setSpectatorLocation(world().getSpawnLocation());
    }
    @GameEventHandler public void quit(GameEvent<PlayerQuitEvent> event) { eliminate(event.getPlayer()); checkWin(); }
    @GameEventHandler public void leave(GameEvent<GamePlayerLeaveEvent> event) { eliminate(event.getPlayer()); checkWin(); }
    @GameEventHandler public void death(GameEvent<PlayerDeathEvent> event) {
        event.clearDrops(); event.setDroppedExp(0);
        eliminate(event.getPlayer()); checkWin();
    }
    boolean running() { return running; }
    boolean isSheriff(GamePlayer player) { return sheriffs.contains(player.uuid()); }
    // TODO: Review text and string identifiers below before release.
    private int maxStrikes() { return Math.max(1, config().getInt("sheriff_strikes", 3)); }
    public List<GameLocation> spawns() {
        // TODO: Review text and string identifiers below before release.
        return world().getPoints("spawn").stream().map(p -> new GameLocation(p.world(),p.blockX()+0.5,p.y(),p.blockZ()+0.5,p.yaw(),p.pitch())).toList();
    }
}
