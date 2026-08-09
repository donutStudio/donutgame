package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.map.GameChest;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWars extends GameModule {
    private static final int COUNTDOWN_TICKS = 200;
    private static final int ROUND_RESTART_DELAY = 200;
    private static final int GAME_END_DELAY = 200;
    private static final int ROUND_OVER_TITLE_DELAY = 10;

    private final VoidWarsPlayers players = new VoidWarsPlayers(this);
    private final VoidWarsEvents roundEvents = new VoidWarsEvents(this);
    private final List<GameChest> chests = new ArrayList<>();
    private final Set<UUID> reconnectRespawns = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    private YamlConfiguration eventsConfig;
    private GameTimer roundTimer;
    private GameBorder mainBorder;
    private GameSidebar sidebar;
    private int round;
    private int alivePlayers;
    private int aliveTeams;
    private boolean roundActive;
    private boolean roundEnding;

    @Override
    public void onLoad() {
        eventsConfig = data().configuration("events");
        playerManager().onPlayerRegistered(players::handleRegisteredPlayer);
        playerManager().onPlayerUnregistered(players::handleUnregisteredPlayer);
        players.bind();
        roundEvents.loadConfiguredEvents();
        roundEvents.bind();
        mapManager().setMap(config().getString("map")).thenRun(this::initializeGame).exceptionally(throwable -> {
            logError("Void Wars failed during map load or initialization.", throwable);
            return null;
        });
    }

    @Override
    public void onUnload() {
        cancelRoundTimer();
        borderManager().clear();
        teamManager().clear();
        mainBorder = null;
        if (sidebar != null) {
            sidebar.delete();
            sidebar = null;
        }
        for (GamePlayer player : playerManager().getPlayers()) {
            player.cancelRespawn();
            player.setNonSpectator();
        }
    }

    void prepareRound() {
        round++;
        roundActive = false;
        roundEnding = false;
        reconnectRespawns.clear();
        cancelRoundTimer();
        borderManager().clear();
        mainBorder = null;
        chests.clear();
        world().setPvp(false);
        players.prepareRoundPlayers();
        GameRegion startBorder = startingBorderRegion();
        if (startBorder != null) {
            mainBorder = borderManager().newBorder(startBorder);
        }
        chests.addAll(roundEvents.spawnChests());
        refreshAliveCounts();
        uiManager().refreshPlayerState();
        startCountdown(COUNTDOWN_TICKS, this::startRound);
    }

    void startTimer(GameTimer timer) {
        roundTimer = timer;
        timer.start();
    }

    void enablePvp() {
        world().setPvp(true);
        uiManager().gameMessage(playerManager().getPlayers(), Component.text("PvP is now enabled!"));
    }

    void collapseGround() {
        GameRegion region = world().region("spawn_platform");
        if (region != null) {
            world().fill(region, org.bukkit.Material.AIR);
            uiManager().sound(playerManager().getPlayers(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
        }
    }

    void checkRoundEnd() {
        if (!roundActive || roundEnding || aliveTeams > 1) {
            return;
        }
        roundEnding = true;
        roundActive = false;
        cancelRoundTimer();
        borderManager().clear();
        mainBorder = null;
        timeManager().newTimer(ROUND_OVER_TITLE_DELAY).onFinish(ignored -> {
            List<GamePlayer> winners = new ArrayList<>(playerManager().getNonSpectators());
            uiManager().title(playerManager().getSpectators(), Component.text("Round Over"));
            uiManager().title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            if (!winners.isEmpty()) {
                String label = winners.size() == 1 ? "Winner: " : "Winners: ";
                uiManager().subtitle(playerManager().getPlayers(), Component.text(label + winners.stream().map(GamePlayer::getName).collect(Collectors.joining(", "))));
            }
            for (GamePlayer winner : winners) {
                winner.setSpectator(winner.location());
            }
        }).start();
        if (round >= maxRounds()) {
            timeManager().newTimer(GAME_END_DELAY).onFinish(ignored -> {
                uiManager().gameMessage(playerManager().getPlayers(), Component.text("Game over!"));
                unloadSelf();
            }).start();
            return;
        }
        timeManager().newTimer(ROUND_RESTART_DELAY).onFinish(ignored -> restartRound()).start();
    }

    void refreshAliveCounts() {
        alivePlayers = 0;
        aliveTeams = 0;
        for (GameTeam team : teamManager().getTeams()) {
            int alive = aliveMembers(team);
            alivePlayers += alive;
            if (alive > 0) {
                aliveTeams++;
            }
        }
    }

    int aliveMembers(GameTeam team) {
        int alive = 0;
        for (GamePlayer player : team.getMembers()) {
            if (!player.isSpectator()) {
                alive++;
            }
        }
        return alive;
    }

    void cancelTeamRespawns(GameTeam team) {
        for (GamePlayer player : team.getMembers()) {
            reconnectRespawns.remove(player.uuid());
            player.cancelRespawn();
        }
    }

    boolean teamEliminated(GameTeam team) {
        return team == null || aliveMembers(team) == 0;
    }

    boolean respawnPossible(GamePlayer player) {
        GameTeam team = player.team();
        return team != null && aliveMembers(team) > 1;
    }

    int respawnTicks(GamePlayer player) {
        GameTeam team = player.team();
        return team == null ? 0 : Math.max(0, aliveMembers(team) * config().getInt("base_respawn_time") * 20);
    }

    GameLocation respawnLocationFor(GamePlayer player) {
        GamePlayer teammate = player.closestTeammate(candidate -> !candidate.isSpectator());
        return teammate == null ? spawn() : teammate.location();
    }

    GameLocation spectatorLocationFor(GameLocation deathLocation) {
        if (deathLocation == null) {
            return spawn();
        }
        GameRegion startBorder = startingBorderRegion();
        if (startBorder != null && !startBorder.contains(deathLocation)) {
            return startBorder.center();
        }
        return deathLocation;
    }

    GameLocation respawnPointAfterDeath(GameLocation deathLocation) {
        return deathLocation != null && deathLocation.y() <= voidThreshold() ? spawn() : deathLocation;
    }

    double voidThreshold() {
        return spawn().y() - 12;
    }

    void markReconnectRespawn(GamePlayer player) {
        reconnectRespawns.add(player.uuid());
    }

    boolean needsReconnectRespawn(GamePlayer player) {
        return reconnectRespawns.contains(player.uuid());
    }

    void clearReconnectRespawn(GamePlayer player) {
        reconnectRespawns.remove(player.uuid());
    }

    List<GameChest> chests() {
        return chests;
    }

    YamlConfiguration eventsConfig() {
        return eventsConfig;
    }

    GameBorder mainBorder() {
        return mainBorder;
    }

    int round() {
        return round;
    }

    boolean roundActive() {
        return roundActive;
    }

    int alivePlayers() {
        return alivePlayers;
    }

    int aliveTeams() {
        return aliveTeams;
    }

    boolean roundEnding() {
        return roundEnding;
    }

    int maxRounds() {
        return config().getInt("max_rounds");
    }

    int totalPlayers() {
        return playerManager().getPlayers().size();
    }

    int totalTeams() {
        int teams = 0;
        for (GameTeam team : teamManager().getTeams()) {
            if (!team.getMembers().isEmpty()) {
                teams++;
            }
        }
        return teams;
    }

    int roundElapsedSeconds() {
        return roundTimer == null ? 0 : roundTimer.getElapsedTicks() / 20;
    }

    void addKill(GamePlayer player) {
        kills.merge(player.uuid(), 1, Integer::sum);
    }

    int kills(GamePlayer player) {
        return kills.getOrDefault(player.uuid(), 0);
    }

    GameLocation spawn() {
        GameLocation spawn = world().point("spawn");
        return spawn == null ? world().spawnLocation() : spawn;
    }

    GameRegion startingBorderRegion() {
        return world().region("starting_border");
    }

    private void initializeGame() {
        teamManager().clear();
        players.assignTeams(config().getInt("team_size"));
        initializeSidebar();
        prepareRound();
    }

    private void startRound() {
        roundActive = true;
        for (GamePlayer player : playerManager().getPlayers()) {
            player.setNonSpectator();
            player.setGameMode(GameMode.SURVIVAL);
            player.clearEffects();
        }
        roundEvents.startRound();
    }

    private void restartRound() {
        mapManager().setMap(config().getString("map")).thenRun(this::prepareRound).exceptionally(throwable -> {
            logError("Failed to restart round " + (round + 1) + ".", throwable);
            return null;
        });
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void initializeSidebar() {
        sidebar = uiManager().newSidebar()
            .fraction("Round", this::round, this::maxRounds)
            .blank()
            .dynamicTime(() -> {
                VoidWarsEvents.SidebarEvent event = roundEvents.sidebarEvent();
                return event == null ? "Overtime" : event.label();
            }, () -> {
                VoidWarsEvents.SidebarEvent event = roundEvents.sidebarEvent();
                return event == null ? 0 : event.remainingSeconds();
            })
            .blank()
            .fraction("Alive Players", this::alivePlayers, this::totalPlayers);
        if (config().getInt("team_size") > 1) {
            sidebar.fraction("Alive Teams", this::aliveTeams, this::totalTeams);
        }
        sidebar.blank().integer("Kills", this::kills).show();
    }
}
