package com.donutsforlife11.donutgame.api.border;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
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

    private static final int PARTICLE_INTERVAL = 8;

    private GameTimer borderDamageTimer;
    private GameTimer particleTimer;

    public BorderManager(GameModule module) {
        this.module = module;
    }

    public void initialize() {
        if (particleTimer != null) {
            return;
        }
        particleTimer = module.timeManager().newTimer().onTick(PARTICLE_INTERVAL, ignored -> {
            for (GameBorder border : borders) {
                border.drawParticles();
            }
        }).start();
    }

    public GameBorder newBorder(GameRegion region) {
        GameBorder border = new GameBorder(
            this,
            module.timeManager(),
            BorderShape.CUBOID,
            region.center(),
            new Vector(
                region.max().x() - region.min().x(),
                region.max().y() - region.min().y(),
                region.max().z() - region.min().z()
            )
        );
        borders.add(border);
        return border;
    }

    public GameBorder newBorder(BorderShape shape, GameLocation center, Vector dimensions) {
        GameBorder border = new GameBorder(this, module.timeManager(), shape, center, dimensions);
        borders.add(border);
        return border;
    }

    public void setBorderDamage(double damage, int interval) {
        this.borderDamage = damage;
        this.borderDamageInterval = interval;

        if (borderDamageTimer != null) {
            borderDamageTimer.cancel();
        }
        borderDamageTimer = module.timeManager().newTimer().onTick(interval, ignored -> {
            for (var player : module.playerManager().getNonSpectators()) {
                Player bukkitPlayer = player.player();
                if (bukkitPlayer == null) {
                    continue;
                }
                boolean insideBorder = false;
                for (GameBorder border : borders) {
                    if (border.containsLocation(player.location())) {
                        insideBorder = true;
                        break;
                    }
                }
                if (!insideBorder) {
                    bukkitPlayer.damage(damage);
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

    GameModule module() {
        return module;
    }
}
