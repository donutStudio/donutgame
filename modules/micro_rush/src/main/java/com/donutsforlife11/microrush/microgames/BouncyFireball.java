package com.donutsforlife11.microrush.microgames;

import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.MicroRush;

// TODO: Native slime collision restitution handles bouncing; the fireball is only the visible passenger.
final class BouncyFireball {
    private final MicroRush game;
    private final BoomBall owner;
    final GameEntity body;
    private GameEntity fireball;
    private UUID lastHitter;
    private int grace = 20;
    private double speed = 0.3;

    BouncyFireball(MicroRush game, BoomBall owner, GameLocation point, Vector direction) {
        this.game = game;
        this.owner = owner;
        body = game.world().summon(point,GameEntities.SLIME);
        owner.track(body);
        body.setAI(false); body.setSize(1); body.setGravity(false);
        body.setInvisible(true); body.setInvulnerable(true); body.setSilent(true);
        body.setAttributeBase(Attribute.BOUNCINESS,1);
        body.setAttributeBase(Attribute.FRICTION_MODIFIER,0);
        body.setAttributeBase(Attribute.AIR_DRAG_MODIFIER,0);
        body.setVelocity(direction.normalize().multiply(speed));
        createVisual();
    }

    private void createVisual() {
        fireball = game.world().summon(body.getLocation(),GameEntities.FIREBALL);
        owner.track(fireball);
        fireball.setGravity(false); fireball.setInvulnerable(true);
        fireball.setExplosionPower(0); fireball.setAcceleration(new Vector());
        fireball.setVelocity(new Vector()); fireball.setGlowing(true);
        body.addPassenger(fireball);
    }

    boolean contains(UUID uuid) { return body.uuid().equals(uuid) || fireball.uuid().equals(uuid); }
    void tick() { if (grace > 0) grace--; if (fireball.isDead() && !body.isDead()) createVisual(); }
    boolean touches(GamePlayer player) {
        if (grace > 0 && (lastHitter == null || lastHitter.equals(player.uuid()))) return false;
        var a = body.getLocation(); var b = player.getLocation();
        return Math.abs(a.y()-b.y()-0.8) < 1.2 && Math.pow(a.x()-b.x(),2)+Math.pow(a.z()-b.z(),2) < 0.9*0.9;
    }
    void hit(GamePlayer player) {
        lastHitter = player.uuid(); grace = 8;
        double yaw = Math.toRadians(player.getLocation().yaw());
        double pitch = Math.toRadians(player.getLocation().pitch());
        speed = Math.min(1.5,speed+0.1);
        body.setVelocity(new Vector(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch)).multiply(speed));
    }
    void accelerate() {
        speed = Math.min(1.5,speed+0.08);
        Vector velocity = body.getVelocity();
        if (velocity.lengthSquared() > 0.0001) body.setVelocity(velocity.normalize().multiply(speed));
    }
}
