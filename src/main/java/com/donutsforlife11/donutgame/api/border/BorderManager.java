package com.donutsforlife11.donutgame.api.border;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class BorderManager {
    private static final int PARTICLE_INTERVAL_TICKS = 10;
    private static final DamageSource OUTSIDE_BORDER_DAMAGE = DamageSource.builder(DamageType.OUTSIDE_BORDER).build();

    private final GameModule module;
    private final Set<GameBorder> borders = new LinkedHashSet<>();
    private double damage = 2.0;
    private int damageInterval = 20;
    private double particleSpacing = 1.75;
    private double particleViewDistance = 40.0;
    private Particle defaultParticle = Particle.TRIAL_OMEN;
    private Particle movingParticle = Particle.RAID_OMEN;
    private GameTimer damageTimer;
    private GameTimer particleTimer;
    private boolean damageTimerArmed;

    public BorderManager(GameModule module) {
        this.module = module;
    }

    public GameBorder newBorder(GameRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("region cannot be null");
        }
        return newBorder(
            BorderShape.CUBOID,
            region.center(),
            new Vector(region.max().x() - region.min().x(), region.max().y() - region.min().y(), region.max().z() - region.min().z())
        );
    }

    public GameBorder newBorder(BorderShape shape, GameLocation center, Vector dimensions) {
        GameBorder border = new GameBorder(this, shape, center, dimensions);
        borders.add(border);
        ensureTimers();
        return border;
    }

    public void setDamage(double damage) {
        if (damage < 0.0) {
            throw new IllegalArgumentException("Border damage cannot be negative.");
        }
        this.damage = damage;
    }

    public void setDamageInterval(int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Border damage interval must be greater than 0.");
        }
        damageInterval = interval;
        if (damageTimer != null) {
            damageTimer.cancel();
            damageTimer = null;
        }
        ensureTimers();
    }

    public Collection<GameBorder> borders() {
        return Collections.unmodifiableSet(borders);
    }

    public void clear() {
        for (GameBorder border : new ArrayList<>(borders)) {
            border.remove();
        }
        stopTimersIfIdle();
    }

    public double damage() {
        return damage;
    }

    public double damageInterval() {
        return damageInterval;
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

    public enum BorderShape {
        CUBOID,
        CYLINDROID,
        ELLIPSOID
    }

    GameModule module() {
        return module;
    }

    TimeManager timeManager() {
        return module.timeManager();
    }

    void remove(GameBorder border) {
        borders.remove(border);
        stopTimersIfIdle();
    }

    private void ensureTimers() {
        if (borders.isEmpty()) {
            return;
        }
        if (particleTimer == null || particleTimer.isCancelled() || particleTimer.isFinished()) {
            particleTimer = module.timeManager().newTimer()
                .onTick(PARTICLE_INTERVAL_TICKS, timer -> drawParticles())
                .start();
        }
        if (damageTimer == null || damageTimer.isCancelled() || damageTimer.isFinished()) {
            damageTimerArmed = false;
            damageTimer = module.timeManager().newTimer()
                .onTick(damageInterval, timer -> damagePlayersOutsideBorders())
                .start();
        }
    }

    private void stopTimersIfIdle() {
        if (!borders.isEmpty()) {
            return;
        }
        if (particleTimer != null) {
            particleTimer.cancel();
            particleTimer = null;
        }
        if (damageTimer != null) {
            damageTimer.cancel();
            damageTimer = null;
            damageTimerArmed = false;
        }
    }

    private void drawParticles() {
        Collection<GameBorder> currentBorders = new ArrayList<>(borders);
        Collection<Player> onlinePlayers = new ArrayList<>();
        for (var gamePlayer : module.playerManager().getOnlinePlayers()) {
            Player player = gamePlayer.bukkitPlayer();
            if (player != null) {
                onlinePlayers.add(player);
            }
        }
        for (GameBorder border : currentBorders) {
            border.drawParticles(onlinePlayers);
        }
    }

    private void damagePlayersOutsideBorders() {
        if (borders.isEmpty() || damage <= 0.0) {
            return;
        }
        if (!damageTimerArmed) {
            damageTimerArmed = true;
            return;
        }
        for (var gamePlayer : module.playerManager().getNonSpectators()) {
            Player player = gamePlayer.bukkitPlayer();
            GameLocation location = gamePlayer.location();
            if (player == null || location == null || insideAnyBorder(location) || !canTakeBorderDamage(player)) {
                continue;
            }
            module.uiManager().actionbar(gamePlayer, Component.text("You are outside the border!", NamedTextColor.RED));
            player.damage(damage, OUTSIDE_BORDER_DAMAGE);
        }
    }

    private boolean insideAnyBorder(GameLocation location) {
        for (GameBorder border : borders) {
            if (border.containsLocation(location)) {
                return true;
            }
        }
        return false;
    }

    private boolean canTakeBorderDamage(Player player) {
        return player.getGameMode() != GameMode.CREATIVE
            && player.getGameMode() != GameMode.SPECTATOR
            && !player.isDead()
            && !player.isInvulnerable()
            && player.getHealth() > 0.0;
    }
}
