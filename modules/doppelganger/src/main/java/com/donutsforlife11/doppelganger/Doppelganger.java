package com.donutsforlife11.doppelganger;

import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Set<UUID> revealed = new HashSet<>();
    private final Map<UUID, Integer> strikes = new HashMap<>();
    private DoppelgangerNpcManager npcs;
    private GameTimer clock;
    private boolean running, ending;
    private boolean soloTesting;
    private int kills, goal, remaining;

    @Override public void onLoad() {
        if (spawns().isEmpty()) throw new IllegalStateException("Doppelganger needs spawn points.");
        world().setSpawnLocation(spawns().getFirst());
        world().setHungerEnabled(false);
        world().setPlayerCollisionsEnabled(false);
        world().gamerules().pvp(false);
        world().gamerules().fallDamage(false);
        world().gamerules().showDeathMessages(false);
        world().gamerules().immediateRespawn(true);
        world().gamerules().spawnMobs(false);
        var players = new ArrayList<>(playerManager().getNonSpectators());
        // RODO: Review text and string identifiers below before release.
        soloTesting = players.size() < 2;
        if (soloTesting) logWarning("Solo practice: opposing-role win checks are disabled.");
        Collections.shuffle(players);
        // RODO: Review text and string identifiers below before release.
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
            player.setInvisible(false);
            player.addEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,-1,0,true);
            player.setSprinting(false);
        }
        npcs = new DoppelgangerNpcManager(this);

        // RODO: Review text and string identifiers below before release.
        goal = Math.clamp(config().getInt("kill_goal", Math.max(1, npcs.botCount())), 1, Math.max(1,npcs.botCount()));
        // RODO: Review text and string identifiers below before release.
        remaining = Math.max(1, config().getInt("game_duration", 300));
        uiManager().newSidebar().setRefreshInterval(20)
            // RODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.integer("Townspeople", () -> npcs.botCount() + alive.size()))
            // RODO: Review text and string identifiers below before release.
            .addEntry(SidebarEntry.time("Time", () -> remaining * 20))
            .addEntry(SidebarEntry.fraction("Kills", () -> kills, () -> goal))
            .addEntry(SidebarEntry.blank())
            .addEntry(SidebarEntry.fraction("Strikes", p -> strikes.getOrDefault(p.uuid(), 0), p -> maxStrikes())).show();
    }

    @Override public void onStart() {
        running = true;
        var starters = new ArrayList<>(playerManager().getNonSpectators());
        for (var player : starters) { player.teleport(player.spawnPoint()); player.setSprinting(false); }
        npcs.createCrowd(starters);
        goal = Math.clamp(config().getInt("kill_goal", Math.max(1, npcs.botCount())), 1, Math.max(1, npcs.botCount()));
        world().gamerules().pvp(true);
        var kits = data().config("kit.yml");
        for(var player : starters) {
            player.setAttributeBase(org.bukkit.attribute.Attribute.MAX_HEALTH,20);
            player.setHealth(20);
            player.resetAttributeBase(org.bukkit.attribute.Attribute.ATTACK_SPEED);
            player.setInvisible(false);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            for(String item : kits.getStringList(isSheriff(player) ? "sheriff" : "doppelganger")) player.giveItem(data().item(item));
            player.addEffect(isSheriff(player) ? org.bukkit.potion.PotionEffectType.RESISTANCE : org.bukkit.potion.PotionEffectType.STRENGTH,-1,
                config().getInt(isSheriff(player) ? "sheriff_resistance" : "doppelganger_strength",isSheriff(player) ? 255 : 20),true);
            if(isSheriff(player) && config().getBoolean("sheriffs_exposed_at_start",false)) reveal(player);
        }
        npcs.startWandering();
        for (GamePlayer player : playerManager().getNonSpectators()) {
            uiManager().title(player, Component.text(isSheriff(player) ? "SHERIFF" : "DOPPELGANGER", isSheriff(player) ? NamedTextColor.BLUE : NamedTextColor.RED));
            uiManager().subtitle(player, Component.text(isSheriff(player) ? "Catch and kill all monstrous Doppelgangers!" : "Blend in and kill townspeople without getting caught!"));
        }
        clock = timeManager().newTimer().onTick(20, timer -> {
            if (timer.elapsedTicks() > 0 && --remaining <= 0) finish(true);
        }).start();
        checkWin();
    }

    private void reveal(GamePlayer sheriff) {
        if (!revealed.add(sheriff.uuid())) return;
        sheriff.addEffect(PotionEffectType.GLOWING, -1, 0, true);
        uiManager().gameMessage(playerManager().getPlayers(),Component.text("Sheriff " + sheriff.name() + " has been revealed!",NamedTextColor.YELLOW));
    }

    private void killedNpc(GamePlayer killer, GameNpc target) {
        var where = target.getLocation();
        var profile = target.profile();
        if(!isSheriff(killer)) {
            if(config().getBoolean("disguise_on_kill",true)) killer.setSkin(profile);
            if(config().getBoolean("teleport_on_kill",true)) killer.teleport(where);
            kills++;
            int ticks = (int)Math.min(72000,Math.max(0,config().getDouble("doppelganger_reveal_time_on_kill",2))*20);
            if (ticks > 0) killer.addEffect(PotionEffectType.GLOWING, ticks, 0, true);
        } else {
            int used = strikes.merge(killer.uuid(),1,Integer::sum);
            uiManager().gameMessage(killer,Component.text("Innocent! Strike " + used + "/" + maxStrikes(),NamedTextColor.RED));
            if(used >= maxStrikes()) eliminate(killer);
        }
        npcs.remove(target);
        if(config().getBoolean("visual_lightning_on_kill",true)) world().visualLightning(where);
        if(config().getBoolean("death_markers",true)) {
            boolean head=false,dust=false;
            for(int radius=0;radius<=2;radius++) for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
                var at = new GameLocation(where.world(),where.blockX()+x,where.blockY(),where.blockZ()+z);
                if(!world().blockAt(at).getType().isAir() || !world().blockAt(new GameLocation(at.world(),at.x(),at.y()-1,at.z())).getType().isSolid()) continue;
                if(!head) { world().setBlock(at,org.bukkit.Material.PLAYER_HEAD); world().blockAt(at).setSkullProfile(profile);head=true; }
                else if(!dust) { world().setBlock(at,org.bukkit.Material.REDSTONE_WIRE);dust=true; }
                if(head && dust) break;
            }
        }
        checkWin();
    }

    private boolean canAct(GamePlayer player) { return running && !ending && alive.contains(player.uuid()) && player.isOnline() && !player.isSpectator(); }

    private void eliminate(GamePlayer player) {
        if (!alive.remove(player.uuid())) return;
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.setSpectator(true, player.getLocation());
        uiManager().title(player, Component.text("Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
    }

    private void checkWin() {
        // RODO: Solo sessions can exercise NPCs without an absent opposing role immediately winning.
        if (soloTesting) {
            if (running && !ending && (npcs.botCount() == 0 || kills >= goal || alive.isEmpty())) finish(false);
            return;
        }
        if (!running || ending) return;
        if (alive.stream().noneMatch(id -> !sheriffs.contains(id))) finish(true);
        else if (npcs.botCount() == 0 || kills >= goal || alive.stream().noneMatch(sheriffs::contains)) finish(false);
    }

    private void finish(boolean sheriffsWon) {
        if (ending) return;
        running = false;
        ending = true;
        if (clock != null) clock.cancel();
        npcs.stop();
        for (GamePlayer player : playerManager().getPlayers()) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            uiManager().title(player, Component.text(isSheriff(player) == sheriffsWon ? "VICTORY" : "DEFEAT",
                isSheriff(player) == sheriffsWon ? NamedTextColor.GOLD : NamedTextColor.DARK_RED, TextDecoration.BOLD));
            uiManager().subtitle(player, Component.text(sheriffsWon ? "All Doppelgangers have been caught!" : "Doppelgangers win!"));
        }
        timeManager().newTimer(240).onFinish(ignored -> unload()).start();
    }

    @GameEventHandler public void move(GameEvent<PlayerMoveEvent> event) {
        if (!running) event.getPlayer().setSprinting(false);
    }
    @GameEventHandler(ignoreCancelled = true) public void damage(GameEvent<EntityDamageEvent> event) {
        double amount = event.getFinalDamage();
        event.setCancelled(true);
        applyHit(event.getAttacker(),event.getEntity(),amount);
    }
    private void applyHit(GamePlayer attacker, com.donutsforlife11.donutgame.api.entity.GameEntityBase entity, double amount) {
        if(!running || ending) return;
        GameNpc npc = entity instanceof GameNpc n ? n : null;
        GamePlayer victim = entity instanceof GamePlayer p ? p : null;
        if(attacker == null || !canAct(attacker)) return;
        if(victim != null) {
            if(victim == attacker || !alive.contains(victim.uuid())) return;
            if(isSheriff(victim)) {
                if(isSheriff(attacker) && !config().getBoolean("sheriffs_can_attack_each_other",false)) return;
                if(!isSheriff(attacker)) {
                    if(!config().getBoolean("doppelgangers_can_attack_sheriffs",true)) return;
                    reveal(victim);
                }
                if(config().getInt("sheriff_resistance",255) >= 4) return;
            }
            if(!isSheriff(attacker) && !isSheriff(victim) && !config().getBoolean("doppelgangers_can_attack_each_other",false)) return;
            if(isSheriff(attacker) && !isSheriff(victim) && !config().getBoolean("sheriffs_can_attack_doppelgangers",true)) return;
            double damage=amount;
            if(damage >= victim.getHealth()) { eliminate(victim);checkWin(); }
            else if(damage > 0) victim.setHealth(victim.getHealth()-damage);
        } else if(npc != null) {
            if(amount >= npc.getHealth()) killedNpc(attacker,npc);
            else if(amount > 0) npc.setHealth(npc.getHealth()-amount);
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
    @Override public void onUnload() {
        if (npcs != null) npcs.stop();
        for (GamePlayer player : playerManager().getPlayers()) player.removePotionEffect(PotionEffectType.GLOWING);
    }

    boolean running() { return running; }
    boolean isSheriff(GamePlayer player) { return sheriffs.contains(player.uuid()); }
    // RODO: Review text and string identifiers below before release.
    private int maxStrikes() { return Math.max(1, config().getInt("sheriff_strikes", 3)); }
    public List<GameLocation> spawns() {
        // RODO: Review text and string identifiers below before release.
        return world().getPoints("spawn").stream().map(p -> new GameLocation(p.world(),p.blockX()+0.5,p.y(),p.blockZ()+0.5,p.yaw(),p.pitch())).toList();
    }
}
