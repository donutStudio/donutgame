package com.donutsforlife11.speedzone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Sound;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.GameSound;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class SpeedZone extends GameModule {
    static final String CHECKPOINT = "checkpoint";

    private final List<GameLocation> checkpoints = new ArrayList<>();
    private double[] segmentLengths;
    private double[] cumulativeLengths;
    private double lapDistance;

    int teamSize;
    double initialBorder;
    double borderCheckpointLoss;
    double borderLapLoss;
    int borderMoveTicks;
    int borderShrinkTicks;

    private SpeedZonePlayers players;
    private SpeedZoneBuildProtection buildProtection;
    private GameBorder border;
    private GameTimer timer;

    private double raceFront;
    private long frontCheckpointEvents;
    private long frontLapEvents;
    private boolean ending;

    @Override
    public void onLoad() {
        readConfig();
        loadCourse();
        configureWorld();

        buildProtection = new SpeedZoneBuildProtection();
        players = new SpeedZonePlayers(this, new SpeedZoneModifiers());
        players.assignTeams();
        players.loadCheckpointModifiers();
        players.setupAllPlayers();

        new SpeedZoneSidebar(this, players).show();
    }

    @Override
    public void onStart() {
        border = borderManager().newBorder(
            BorderShape.CUBOID,
            checkpoints.get(0),
            new Vector(initialBorder, initialBorder, initialBorder)
        );

        timer = timeManager().newTimer()
            .onTick(tick -> tickGame(tick.elapsedTicks()))
            .start();
    }

    private void tickGame(int elapsedTicks) {
        if (ending) {
            return;
        }

        players.tickCheckpoints();

        double leadingProgress = players.leadingProgress();
        if (leadingProgress > raceFront) {
            raceFront = leadingProgress;
            updateFrontShrink();
        }

        if (border != null && (elapsedTicks % Math.max(1, borderMoveTicks) == 0)) {
            border.setCenter(locationAtProgress(raceFront), Math.max(0, borderMoveTicks));
        }

        players.checkGameOver();
    }

    private void updateFrontShrink() {
        boolean changed = false;
        while (true) {
            long zeroBasedEvent = frontCheckpointEvents;
            long lap = zeroBasedEvent / checkpoints.size();
            int segment = (int) (zeroBasedEvent % checkpoints.size());
            if (raceFront + 1.0e-7 < lap * lapDistance + cumulativeLengths[segment + 1]) {
                break;
            }
            frontCheckpointEvents++;
            if (frontCheckpointEvents % checkpoints.size() == 0) {
                frontLapEvents++;
            }
            changed = true;
        }
        if (!changed || border == null) {
            return;
        }

        double checkpointLoss = initialBorder * borderCheckpointLoss / checkpoints.size();
        double lapLoss = initialBorder * borderLapLoss;
        double width = Math.max(0.0,
            initialBorder - frontCheckpointEvents * checkpointLoss - frontLapEvents * lapLoss);
        border.setDimensions(new Vector(width, width, width), borderShrinkTicks);
    }

    GameLocation locationAtProgress(double progress) {
        if (lapDistance <= 0.0) {
            return checkpoints.get(0);
        }
        double inLap = progress % lapDistance;
        if (inLap < 0.0) {
            inLap += lapDistance;
        }

        int segment = checkpoints.size() - 1;
        for (int i = 0; i < segmentLengths.length; i++) {
            if (inLap <= cumulativeLengths[i + 1]) {
                segment = i;
                break;
            }
        }

        double length = segmentLengths[segment];
        double t = length <= 1.0e-9 ? 0.0 : (inLap - cumulativeLengths[segment]) / length;
        t = Math.clamp(t, 0.0, 1.0);
        GameLocation a = checkpoints.get(segment);
        GameLocation b = checkpoints.get((segment + 1) % checkpoints.size());
        return new GameLocation(
            world(),
            a.x() + (b.x() - a.x()) * t,
            a.y() + (b.y() - a.y()) * t,
            a.z() + (b.z() - a.z()) * t,
            (float) a.yaw(),
            (float) a.pitch()
        );
    }

    double progress(int completedLaps, int segmentIndex, GameLocation position) {
        GameLocation a = checkpoints.get(segmentIndex);
        GameLocation b = checkpoints.get((segmentIndex + 1) % checkpoints.size());
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        double t = lengthSquared <= 1.0e-12 ? 0.0 :
            ((position.x() - a.x()) * dx + (position.y() - a.y()) * dy + (position.z() - a.z()) * dz) / lengthSquared;
        t = Math.clamp(t, 0.0, 1.0);
        return completedLaps * lapDistance + cumulativeLengths[segmentIndex] + t * segmentLengths[segmentIndex];
    }

    boolean passedNextCheckpoint(int segmentIndex, GameLocation position) {
        GameLocation a = checkpoints.get(segmentIndex);
        GameLocation b = checkpoints.get((segmentIndex + 1) % checkpoints.size());
        return (position.x() - b.x()) * (b.x() - a.x())
            + (position.y() - b.y()) * (b.y() - a.y())
            + (position.z() - b.z()) * (b.z() - a.z()) >= 0.0;
    }

    void finish(Collection<GamePlayer> winners) {
        if (ending) {
            return;
        }
        ending = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        uiManager().title(playerManager().getSpectators(), Component.text("Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
        uiManager().playSound(playerManager().getSpectators(), GameSound.of(Sound.BLOCK_BEACON_DEACTIVATE).volume(1f).pitch(1.4f));

        if (!winners.isEmpty()) {
            uiManager().title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            uiManager().subtitle(playerManager().getPlayers(), winnerSubtitle(winners));
            uiManager().playSound(winners, GameSound.of(Sound.UI_TOAST_CHALLENGE_COMPLETE).volume(1f).pitch(1.675f));
        }

        timeManager().newTimer(100).onFinish(ignored -> unload()).start();
    }

    private Component winnerSubtitle(Collection<GamePlayer> winners) {
        if (teamSize <= 1) {
            GamePlayer winner = winners.iterator().next();
            String name = winner.bukkitPlayer() == null ? winner.uuid().toString() : winner.bukkitPlayer().getName();
            return Component.text("Winner: " + name);
        }
        GameTeam team = winners.iterator().next().getTeam();
        return Component.text("Winning Team: ").append(team == null ? Component.text("Unknown Team") : team.displayName());
    }

    private void readConfig() {
        teamSize = Math.max(1, config().getInt("team_size", 1));
        initialBorder = Math.max(0.0, config().getDouble("initial_border", 50.0));
        borderCheckpointLoss = Math.max(0.0, config().getDouble("border_checkpoint_loss", 0.05));
        borderLapLoss = Math.max(0.0, config().getDouble("border_lap_loss", 0.10));
        borderMoveTicks = Math.max(1, config().getInt("border_move_ticks", 3));
        borderShrinkTicks = Math.max(0, config().getInt("border_shrink_ticks", 15));
    }

    private void loadCourse() {
        checkpoints.clear();
        checkpoints.addAll(world().getPoints(CHECKPOINT));
        if (checkpoints.size() < 2) {
            throw new IllegalStateException("Speed Zone map needs at least two 'checkpoint' points.");
        }

        segmentLengths = new double[checkpoints.size()];
        cumulativeLengths = new double[checkpoints.size() + 1];
        for (int i = 0; i < checkpoints.size(); i++) {
            GameLocation a = checkpoints.get(i);
            GameLocation b = checkpoints.get((i + 1) % checkpoints.size());
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            segmentLengths[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (segmentLengths[i] <= 1.0e-6) {
                throw new IllegalStateException("Speed Zone checkpoint " + i + " overlaps checkpoint " + ((i + 1) % checkpoints.size()) + ".");
            }
            cumulativeLengths[i + 1] = cumulativeLengths[i] + segmentLengths[i];
        }
        lapDistance = cumulativeLengths[checkpoints.size()];
    }

    private void configureWorld() {
        world().setWorldSpawn(checkpoints.get(0));
        world().setHungerEnabled(false);
        world().gamerules().fallDamage(false);
        world().gamerules().pvp(false);
    }

    List<GameLocation> checkpoints() {
        return checkpoints;
    }

    GameBorder border() {
        return border;
    }
}
