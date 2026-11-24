package com.donutsforlife11.microrush.microgames;

import java.util.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.*;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;

public final class BoomBall extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    private final List<BouncyFireball> balls = new ArrayList<>();
    private int server;
    // RODO: Review text and string identifiers below before release.
    public BoomBall(MicroRush game, GameMap map) { super(game,map,"boom_ball"); }
    // RODO: Review text and string identifiers below before release.
    @Override public void validate() { requireRegion("objective_a"); requireRegion("objective_b"); }
    @Override protected void effects(GamePlayer player) {
        player.addEffect(PotionEffectType.SPEED,-1,3,true);
        player.addEffect(PotionEffectType.JUMP_BOOST,-1,1,true);
        player.addEffect(PotionEffectType.RESISTANCE,-1,255,true);
    }
    @Override public void start() {
        game.team(0).setFriendlyFire(false); game.team(1).setFriendlyFire(false);
        every(240, () -> {
            // RODO: Review text and string identifiers below before release.
            if (balls.size() >= Math.max(1,game.config().getInt("microgames.boom_ball.max_balls",8))) return;
            GameLocation start = spawn(server);
            GameLocation other = spawn(1-server);
            balls.add(new BouncyFireball(game,this,new GameLocation(start.x(),start.y()+1,start.z()),
                new Vector(other.x()-start.x(),0,other.z()-start.z())));
            server = 1-server;
        });
        every(200, () -> balls.forEach(BouncyFireball::accelerate));
        every(1, () -> {
            for (BouncyFireball ball : balls) {
                // RODO: Review text and string identifiers below before release.
                if (ball.body.isDead()) { game.restartPoint(); return; }
                ball.tick();
                // RODO: Review text and string identifiers below before release.
                for (int team = 0; team < 2; team++) if (nearGoal(ball.body.getLocation(),team,1.5)) {
                    game.score(1-team,true); return;
                }
                if (!map.getRegion("arena").contains(ball.body.getLocation())) { game.restartPoint(); return; }
                for (GamePlayer player : game.playerManager().getNonSpectators()) if (ball.touches(player)) {
                    game.score(1-game.team(player),true); return;
                }
            }
        });
    }
    @Override public void damage(GameEvent<EntityDamageEvent> event) { event.setCancelled(true); }
    @Override public void attack(GameEvent<PrePlayerAttackEntityEvent> event) {
        for (BouncyFireball ball : balls) if (ball.contains(event.getAttacked().uuid())) {
            event.setCancelled(true); ball.hit(event.getPlayer()); return;
        }
    }
    @Override public void projectile(GameEvent<ProjectileHitEvent> event) {
        if (balls.stream().anyMatch(ball -> ball.contains(event.getEntity().uuid()))) {
            event.setCancelled(true);
            if (event.getHitEntity() instanceof GamePlayer player && !player.isSpectator()) game.score(1-game.team(player),true);
        }
    }
    @Override public void move(GameEvent<PlayerMoveEvent> event) {
        super.move(event);
        // RODO: Review text and string identifiers below before release.
        if (otherSide(event.getPlayer(),event.getTo()) || map.getRegion("objective_a").contains(event.getTo()) || map.getRegion("objective_b").contains(event.getTo())) event.setTo(event.getFrom());
    }
}
