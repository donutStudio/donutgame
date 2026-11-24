package com.donutsforlife11.microrush.microgames;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;

public final class SlimeShot extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    private GameEntity ball;
    private final java.util.Set<java.util.UUID> hits = new java.util.HashSet<>();
    // TODO: Review text and string identifiers below before release.
    public SlimeShot(MicroRush game, GameMap map) { super(game, map, "slime_shot"); }
    // TODO: Review text and string identifiers below before release.
    @Override public void validate() { requireRegion("objective_a"); requireRegion("objective_b"); requirePoint("objective_d"); }
    @Override protected void effects(GamePlayer player) {
        player.addEffect(PotionEffectType.SPEED,-1,1,true);
        player.addEffect(PotionEffectType.RESISTANCE,-1,255,true);
    }
    @Override public void start() {
        game.team(0).setFriendlyFire(true); game.team(1).setFriendlyFire(true);
        // TODO: Review text and string identifiers below before release.
        ball = game.world().summon(center(map.getPoint("objective_d")), GameEntities.SLIME);
        track(ball);
        ball.setAI(false); ball.setSize(1);
        ball.setAttributeBase(Attribute.SCALE,2);
        // TODO: Review text and string identifiers below before release.
        ball.setAttributeBase(Attribute.BOUNCINESS, Math.clamp(game.config().getDouble("microgames.slime_shot.bounciness",0.1),0,1));
        ball.setInvulnerable(false); ball.setGlowing(true);
        every(2, () -> {
            // TODO: Review text and string identifiers below before release.
            if (ball.isDead()) { game.restartPoint(); return; }
            // TODO: Review text and string identifiers below before release.
            for (int team = 0; team < 2; team++) if (nearGoal(ball.getLocation(),team,1.5)) {
                game.score(1-team,true); return;
            }
            if (!map.getRegion("arena").contains(ball.getLocation())) { game.restartPoint(); return; }
            // TODO: Review text and string identifiers below before release.
            for (var pad : map.getPoints("objective_c")) if (distance(ball.getLocation(),pad) <= 1.7*1.7)
                ball.setVelocity(ball.getVelocity().setY(1));
        });
        every(20, () -> { if (!ball.isDead()) game.world().particle(Particle.HAPPY_VILLAGER,ball.getLocation(),3); });
    }
    @Override public void damage(GameEvent<org.bukkit.event.entity.EntityDamageEvent> event) { event.setCancelled(true); }
    @Override public void attack(GameEvent<PrePlayerAttackEntityEvent> event) {
        if (ball == null || !event.getAttacked().uuid().equals(ball.uuid())) return;
        event.setCancelled(true);
        GamePlayer player = event.getPlayer();
        if (!hits.add(player.uuid())) return;
        after(7, () -> hits.remove(player.uuid()));
        var at = player.getLocation();
        double yaw = Math.toRadians(at.yaw());
        ball.setVelocity(new Vector(-Math.sin(yaw)*1.2,0.4,Math.cos(yaw)*1.2));
    }
    @Override public void move(GameEvent<PlayerMoveEvent> event) {
        super.move(event);
        // TODO: Review text and string identifiers below before release.
        for (var pad : map.getPoints("objective_c")) if (distance(event.getTo(),pad) <= 1.7*1.7)
            event.getPlayer().addEffect(PotionEffectType.JUMP_BOOST,20,5,true);
    }
}
