package com.donutsforlife11.microrush;

import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.*;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.microrush.GameKits;
import com.donutsforlife11.donutgame.api.item.GameItem;

public abstract class Microgame {
    protected final MicroRush game;
    protected final GameMap map;
    private final String id;
    private final List<GameTimer> timers = new ArrayList<>();
    private final List<GameEntity> entities = new ArrayList<>();
    private final Map<UUID, GameItem> ammunition = new HashMap<>();

    protected Microgame(MicroRush game, GameMap map, String id) { this.game = game; this.map = map; this.id = id; }
    public final String name() { return MicroRush.displayName(id); }
    public void validate() { }
    public void start() { }
    public boolean beginOvertime() { return false; }
    public void setupPlayer(GamePlayer player) {
        GameLocation spawn = spawn(game.team(player));
        player.cancelRespawn();
        player.setSpawnPoint(spawn);
        player.setSpectator(false, spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.clearActivePotionEffects();
        player.clearItems();
        player.heal();
        player.setFoodLevel(20);
        player.clearExperience();
        GameKits.give(game, player, game.kit(id));
        effects(player);
        game.prepareLayout(player);
        // TODO: Review text and string identifiers below before release.
        if (id.equals("sniper_showdown") || id.equals("terror_tower")) {
            player.inventory().stream().filter(item -> item != null && item.getType() == Material.ARROW).findFirst()
                .ifPresent(item -> ammunition.put(player.uuid(), item));
        }
    }
    protected void effects(GamePlayer player) { }
    public void lateJoin(GamePlayer player) { setupPlayer(player); }
    public void quit(GamePlayer player) { }
    public void death(GamePlayer player) {
        // TODO: Review text and string identifiers below before release.
        player.respawn(Math.max(0, game.config().getInt("respawn_delay", 1))*20, () -> {
            effects(player);
            return spawn(game.team(player));
        });
    }
    public void move(GameEvent<PlayerMoveEvent> event) {
        // TODO: Review text and string identifiers below before release.
        if (event.getTo().y() < map.getRegion("arena").min().y()-2) event.getPlayer().kill();
    }
    public void breakBlock(GameEvent<BlockBreakEvent> event) { event.setCancelled(true); }
    public void placeBlock(GameEvent<BlockPlaceEvent> event) { event.setCancelled(true); }
    public void damage(GameEvent<EntityDamageEvent> event) { }
    public void attack(GameEvent<PrePlayerAttackEntityEvent> event) { }
    public void projectile(GameEvent<ProjectileHitEvent> event) { }
    public void launched(GameEvent<ProjectileLaunchEvent> event) {
        GamePlayer player = event.getShooter();
        if (player != null && ammunition.containsKey(player.uuid())) after(1, () -> {
            if (!player.isSpectator() && player.inventory().stream().noneMatch(item -> item != null && item.getType() == Material.ARROW)) {
                GameItem arrow = new GameItem(ammunition.get(player.uuid()).copyBukkitItem());
                arrow.setAmount(1);
                player.giveItem(arrow);
            }
        });
    }
    public void stop() {
        timers.forEach(GameTimer::cancel); timers.clear();
        entities.forEach(entity -> { if (!entity.isDead()) entity.remove(); }); entities.clear();
    }
    public final void track(GameEntity entity) { entities.add(entity); }
    protected final void after(int ticks, Runnable action) {
        timers.add(game.timeManager().newTimer(ticks).onFinish(ignored -> { if (active()) action.run(); }).start());
    }
    protected final void every(int ticks, Runnable action) {
        timers.add(game.timeManager().newTimer().onTick(ticks, ignored -> { if (active()) action.run(); }).start());
    }
    protected final boolean active() { return game.playing(this); }
    public GameLocation spawn(int team) {
        // TODO: Review text and string identifiers below before release.
        GameLocation point = map.getPoint(team == 0 ? "red_spawn" : "blue_spawn");
        return new GameLocation(point.world(),point.blockX()+0.5,point.y(),point.blockZ()+0.5,point.yaw(),point.pitch());
    }
    // TODO: Review text and string identifiers below before release.
    protected final void requirePoint(String name) { if (map.getPoint(name) == null) throw new IllegalArgumentException(name() + " needs point " + name); }
    // TODO: Review text and string identifiers below before release.
    protected final void requireRegion(String name) { if (map.getRegion(name) == null) throw new IllegalArgumentException(name() + " needs region " + name); }
    protected final boolean sameBlock(GameLocation a, GameLocation b) { return a.blockX()==b.blockX() && a.blockY()==b.blockY() && a.blockZ()==b.blockZ(); }
    protected final double distance(GameLocation a, GameLocation b) { return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2); }
    protected final GameLocation center(GameLocation point) {
        return new GameLocation(point.world(),point.blockX()+0.5,point.y(),point.blockZ()+0.5,point.yaw(),point.pitch());
    }
    protected final boolean enemyHalf(GamePlayer player, GameLocation location) {
        // TODO: Review text and string identifiers below before release.
        GameRegion region = map.getRegion(game.team(player)==0 ? "blue_spawn" : "red_spawn");
        return region != null && region.contains(location);
    }
    protected final boolean water(GameLocation at) { return game.world().blockAt(at).is(Material.WATER); }
}
