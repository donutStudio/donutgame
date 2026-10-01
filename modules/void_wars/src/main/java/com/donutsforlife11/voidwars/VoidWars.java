package com.donutsforlife11.voidwars;

import java.util.Collection;

import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.GameSound;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWars extends GameModule {
    static final String SPAWN = "spawn";
    static final String BORDER = "starting_border";
    static final String MUTABLE = "mutable_zone";
    static final String SPAWN_PLATFORM = "spawn_platform";
    static final String CHEST = "chest";

    int maxRounds;
    int teamSize;
    int baseRespawnTicks;
    int groundCollapseTicks;
    int pvpTicks;
    int minChests;
    int maxChests;
    boolean protectRegions;

    private int round;
    private boolean roundStarted;
    private boolean roundEnding;
    private GameTimer roundTimer;
    private GameBorder border;
    private VoidWarsEvents events;
    private VoidWarsPlayers players;
    // private VoidWarsRegionProtection regionProtection;
    private VoidWarsSidebar sidebar;

    @Override
    public void onLoad() {
        readConfig();
        validateMap();
        events = new VoidWarsEvents(this);
        players = new VoidWarsPlayers(this);
        new VoidWarsRegionProtection(this);
        events.load();
        players.assignTeams();
        loadRound();
        sidebar = new VoidWarsSidebar(this, events, players);
        sidebar.show();
    }

    @Override
    public void onStart() {
        startRound();
    }

    @Override
    public void onReload() {
        loadRound();
    }

    void loadRound() {
        loadRound(false);
    }

    void loadRound(boolean preservePlayerLocations) {
        round++;
        roundStarted = false;
        roundEnding = false;
        players.cancelRespawns();
        world().setHungerEnabled(false);
        world().gamerules().fallDamage(false);
        world().gamerules().pvp(false);
        world().setWorldSpawn(world().getPoint(SPAWN));
        border = borderManager().newBorder(world().getRegion(BORDER));
        events.prepareRound();

        for (GamePlayer player : playerManager().getPlayers()) {
            players.setupPlayer(player, true, preservePlayerLocations);
            player.addEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
    }

    private void startRound() {
        roundStarted = true;
        world().gamerules().fallDamage(true);
        world().setHungerEnabled(true);
        for (GamePlayer player : playerManager().getPlayers()) {
            player.clearEffect(PotionEffectType.INVISIBILITY);
        }
        roundTimer = timeManager().newTimer()
            .onTick(20, timer -> events.tickRound(timer.elapsedTicks()))
            .start();
    }

    void endRound(Collection<GamePlayer> winners) {
        if (roundEnding || !roundStarted) {
            return;
        }
        roundEnding = true;
        roundStarted = false;
        players.cancelRespawns();
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (border != null) {
            border.remove();
            border = null;
        }
        timeManager().newTimer(round >= maxRounds ? 100 : 50).onFinish(timer -> {
            uiManager().title(playerManager().getSpectators(), Component.text("Round Over!"));
            uiManager().playSound(playerManager().getSpectators(), GameSound.of(Sound.BLOCK_BEACON_DEACTIVATE).volume(1f).pitch(1.5f));
            uiManager().title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            uiManager().playSound(winners, GameSound.of(Sound.UI_TOAST_CHALLENGE_COMPLETE).volume(1f).pitch(1.675f));
            if (!winners.isEmpty()) {
                uiManager().subtitle(playerManager().getPlayers(), winnerSubtitle(winners));
            }
            for (GamePlayer winner : winners) {
                winner.setSpectator(true, winner.location());
            }
            if (round >= maxRounds) {
                timeManager().newTimer(100).onFinish(ignored -> {
                    uiManager().title(playerManager().getPlayers(), Component.text("Game over!", NamedTextColor.WHITE, TextDecoration.BOLD));
                    timeManager().newTimer(40).onFinish(ignored2 -> unload()).start();
                }).start();
            } else {
                timeManager().newTimer(100).onFinish(ignored -> resetForNextRound()).start();
            }
        }).start();
    }

    private void resetForNextRound() {
        reload().exceptionally(throwable -> {
            logError("Failed to reset Void Wars for the next round.", throwable);
            return null;
        });
    }

    private Component winnerSubtitle(Collection<GamePlayer> winners) {
        if (teamSize <= 1) {
            GamePlayer winner = winners.iterator().next();
            return Component.text("Winner: " + (winner.bukkitPlayer() == null ? winner.uuid().toString() : winner.bukkitPlayer().getName()));
        }
        GameTeam team = winners.iterator().next().getTeam();
        return Component.text("Winning Team: ").append(team == null ? Component.text("Unknown Team") : team.displayName());
    }

    GameTimer roundTimer() {
        return roundTimer;
    }

    GameBorder border() {
        return border;
    }

    int round() {
        return round;
    }

    boolean roundStarted() {
        return roundStarted;
    }

    boolean roundEnding() {
        return roundEnding;
    }

    private void readConfig() {
        maxRounds = Math.max(1, config().getInt("max_rounds", 3));
        teamSize = Math.max(1, config().getInt("team_size", 1));
        baseRespawnTicks = Math.max(0, config().getInt("base_respawn_time", 20)) * 20;
        groundCollapseTicks = Math.max(0, config().getInt("ground_collapse_time", 15)) * 20;
        pvpTicks = Math.max(0, config().getInt("pvp_enablement_time", 20)) * 20;
        minChests = Math.max(0, config().getInt("min_chests", 0));
        maxChests = Math.max(minChests, config().getInt("max_chests", minChests));
        protectRegions = config().getBoolean("protect_regions", true);
    }

    private void validateMap() {
        require(SPAWN, world().getPoint(SPAWN) != null);
        require(BORDER, world().getRegion(BORDER) != null);
        require(SPAWN_PLATFORM, !world().getRegions(SPAWN_PLATFORM).isEmpty());
        if (protectRegions) {
            require(MUTABLE, world().getRegion(MUTABLE) != null);
        }
        if (maxChests > 0) {
            require(CHEST, !world().getPoints(CHEST).isEmpty());
        }
    }

    private void require(String mapObject, boolean valid) {
        if (!valid) {
            throw new IllegalStateException("Void Wars map is missing required object '" + mapObject + "'.");
        }
    }

}
