package com.donutsforlife11.donutgame.api.border;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class BorderManager {
    private final GameModule module;

    private double particleSpacing = 1.75;
    private double particleViewDistance = 40.0;
    private Particle defaultParticle = Particle.TRIAL_OMEN;
    private Particle movingParticle = Particle.RAID_OMEN;

    private final Set<GameBorder> borders = new HashSet<>();

    private double borderDamage = 2;
    private int borderDamageInterval = 20;

    private static final int PARTICLE_INTERVAL = 10;

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
        setBorderDamage(borderDamage, borderDamageInterval);
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
        if (damage < 0) {
            throw new IllegalArgumentException("Border damage cannot be negative.");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Border damage interval must be greater than zero.");
        }
        this.borderDamage = damage;
        this.borderDamageInterval = interval;
        if (borderDamageTimer != null) {
            borderDamageTimer.cancel();
        }
        borderDamageTimer = module.timeManager().newTimer()
            .onTick(interval, ignored -> {
                // No borders means there is currently no border restriction.
                if (borders.isEmpty()) {
                    return;
                }
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
                    if (insideBorder) {
                        continue;
                    }
                    if (!canTakeBorderDamage(bukkitPlayer)) {
                        continue;
                    }
                    module.uiManager().actionbar(
                        player,
                        Component.text(
                            "You are outside the border!",
                            NamedTextColor.RED
                        )
                    );
                    bukkitPlayer.damage(
                        damage,
                        DamageSource.builder(DamageType.OUTSIDE_BORDER).build()
                    );
                }
            }).start();
    }

    public Collection<GameBorder> borders() {
        return borders;
    }

    public void clear() {
        for (GameBorder border : Set.copyOf(borders)) {
            border.remove();
        }
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

    private boolean canTakeBorderDamage(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (player.isDead() || player.isInvulnerable()) {
            return false;
        }
        return !(player instanceof Damageable damageable) || damageable.getHealth() > 0.0;
    }
}
