package com.donutsforlife11.microrush.microgames;

import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.MicroRush;

// A single native fireball; bounded swept probes reflect its velocity.
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
        body = game.world().summon(point,GameEntities.FIREBALL);
        owner.track(body);
        body.setGravity(false); body.setInvulnerable(true);
        body.setExplosionPower(0); body.setAcceleration(new Vector());
        body.setGlowing(true);
        velocity = direction.normalize().multiply(speed);
        body.setVelocity(velocity);
    }

    private Vector velocity;
    boolean contains(UUID uuid) { return body.uuid().equals(uuid); }
    void tick() {
        if (grace > 0) grace--;
        var p = body.getLocation();
        // Bounded swept probes reflect the velocity before native projectile collision.
        int steps = Math.max(1, (int)Math.ceil(speed / 0.2));
        double x=p.x(),y=p.y(),z=p.z();
        for(int i=0;i<steps;i++) {
            double dx=velocity.getX()/steps,dy=velocity.getY()/steps,dz=velocity.getZ()/steps;
            if(solid(p,x+dx+Math.signum(dx)*0.3,y,z)) { velocity.setX(-velocity.getX()); velocity.multiply(0.85); speed=velocity.length(); break; }
            if(solid(p,x,y+dy+Math.signum(dy)*0.3,z)) { velocity.setY(-velocity.getY()); velocity.multiply(0.85); speed=velocity.length(); break; }
            if(solid(p,x,y,z+dz+Math.signum(dz)*0.3)) { velocity.setZ(-velocity.getZ()); velocity.multiply(0.85); speed=velocity.length(); break; }
            x+=dx;y+=dy;z+=dz;
        }
        body.setVelocity(velocity);
    }
    private boolean solid(GameLocation p,double x,double y,double z) {
        return game.world().blockAt(new GameLocation(p.world(),x,y,z)).getType().isSolid();
    }
    boolean touches(GamePlayer player) {
        if (grace > 0 && (lastHitter == null || lastHitter.equals(player.uuid()))) return false;
        var a = body.getLocation(); var b = player.getLocation();
        return Math.abs(a.y()-b.y()-0.8) < 1.2 && Math.pow(a.x()-b.x(),2)+Math.pow(a.z()-b.z(),2) < 0.9*0.9;
    }
    void hit(GamePlayer player) {
        lastHitter = player.uuid(); grace = 8;
        double yaw = Math.toRadians(player.getLocation().yaw());
        double pitch = Math.toRadians(player.getLocation().pitch());
        speed = Math.min(0.9,speed+0.05);
        velocity = new Vector(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch)).multiply(speed);
        body.setVelocity(velocity);
    }
    void accelerate() {
        speed = Math.min(0.9,speed+0.03);

        if (velocity.lengthSquared() > 0.0001) velocity.normalize().multiply(speed);
        body.setVelocity(velocity);
    }
}
