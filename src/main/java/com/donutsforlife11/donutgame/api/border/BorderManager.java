package com.donutsforlife11.donutgame.api.border;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class BorderManager {
    private final GameModule module;

    private double particleSpacing = 1.0;
    private double particleViewDistance = 32.0;
    private Particle defaultParticle = Particle.TRIAL_OMEN;
    private Particle movingParticle = Particle.RAID_OMEN;

    private final Set<GameBorder> borders = new HashSet<>();

    private double borderDamage = 2;
    private int borderDamageInterval = 20;

    private static int PARTICLE_INTERVAL = 8;

    private GameTimer borderDamageTimer;
    private GameTimer particleTimer;

    public BorderManager(GameModule module) {
        this.module = module;
        particleTimer = module.timeManager().newTimer().onTick(PARTICLE_INTERVAL, ignored -> {
            for (GameBorder border : borders) {
                border.drawParticles();
            }
        });
        particleTimer.start();
    }

    public GameBorder newBorder(BoundingBox box) {
        Location center = new Location(module.world().getBukkitWorld(), box.getCenterX(), box.getCenterY(), box.getCenterZ());
        Vector dimensions = new Vector(box.getWidthX(), box.getHeight(), box.getWidthZ());
        return new GameBorder(this, module.timeManager(), BorderShape.CUBOID, center, dimensions);
    }
    public GameBorder newBorder(BorderShape shape, Location center, Vector dimensions) {
        return new GameBorder(this, module.timeManager(), shape, center, dimensions);
    }

    public void setBorderDamage(double damage, int interval) {
        this.borderDamage = damage;
        this.borderDamageInterval = interval;

        borderDamageTimer.cancel();
        borderDamageTimer = module.timeManager().newTimer().onTick(interval, ignored -> {
            for (Player player : module.playerManager().getNonSpectators()) {
                boolean insideBorder = false;
                for (GameBorder border : borders) {
                    if (border.containsLocation(player.getLocation())) {
                        insideBorder = true;
                        break;
                    }
                }
                if (!insideBorder) {
                    player.damage(damage);
                }
            }
        }).start();
    }

    public Collection<GameBorder> borders() {
        return borders;
    }
    public double damage() {
        return borderDamage;
    }
    public double interval() {
        return borderDamageInterval;
    }

    public enum BorderShape {
        CUBOID,
        CYLINDROID,
        ELLIPSOID
    }

    public double particleSpacing() {
        return particleSpacing;
    }
    public double particleViewDistance() {
        return particleViewDistance;
    }
    public Particle defaultParticle() {
        return defaultParticle;
    }
    public Particle movingParticle() {
        return movingParticle;
    }
}
