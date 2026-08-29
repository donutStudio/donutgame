package com.donutsforlife11.donutgame.api.border;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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

    private final Set<GameBorder> borders = new LinkedHashSet<>();

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
        setBorderDamage(damage);
        setBorderInterval(interval);
    }

    public void setBorderDamage(double damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("Border damage cannot be negative.");
        }
        this.borderDamage = damage;
    }

    public void setBorderInterval(int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Border damage interval must be greater than zero.");
        }
        this.borderDamageInterval = interval;
        if (borderDamageTimer != null) borderDamageTimer.cancel();
        borderDamageTimer = module.timeManager().newTimer()
            .onTick(interval, ignored -> damagePlayersOutsideBorders())
            .start();
    }

    public Collection<GameBorder> borders() {
        return Collections.unmodifiableSet(borders);
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

    void remove(GameBorder border) {
        borders.remove(border);
    }

    private void damagePlayersOutsideBorders() {
        if (borders.isEmpty()) return;
        for (var player : module.playerManager().getNonSpectators()) {
            Player bukkitPlayer = player.player();
            if (bukkitPlayer == null || insideAnyBorder(player.location()) || !canTakeBorderDamage(bukkitPlayer)) continue;
            module.uiManager().actionbar(player, Component.text("You are outside the border!", NamedTextColor.RED));
            bukkitPlayer.damage(borderDamage, DamageSource.builder(DamageType.OUTSIDE_BORDER).build());
        }
    }

    private boolean insideAnyBorder(GameLocation location) {
        for (GameBorder border : borders) {
            if (border.containsLocation(location)) return true;
        }
        return false;
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
