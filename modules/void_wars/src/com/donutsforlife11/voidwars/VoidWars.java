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
    private final VoidWarsSidebar roundSidebar = new VoidWarsSidebar(this);
    private final List<GameChest> chests = new ArrayList<>();
    private final Set<UUID> reconnectRespawns = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private YamlConfiguration eventsConfig;
    private GameTimer roundTimer;
    private GameBorder mainBorder;
    private int round;
    private int alivePlayers;
    private int aliveTeams;
    private boolean roundActive;
    private boolean roundEnding;

    @Override
    public void onLoad() {
        eventsConfig = config("events.yml");
        playerManager().onPlayerRegistered(player -> players.handleRegisteredPlayer(player));
        playerManager().onPlayerUnregistered(player -> players.handleUnregisteredPlayer(player));
        players.bind();
        roundEvents.loadConfiguredEvents();
        roundEvents.bind();
        mapManager().setMap(config().getString("map")).thenRun(() -> initializeGame()).exceptionally(throwable -> {
            logError("Void Wars failed during map load or initialization.", throwable);
            return null;
        });
    }

    @Override
    public void onReload() {
        restartRound();
    }

    @Override
    public void onUnload() {
        cancelRoundTimer();
        borderManager().clear();
        mainBorder = null;
        roundSidebar.clear();
        for (GamePlayer player : playerManager().getPlayers()) {
            player.cancelRespawn();
            if (player.bukkitPlayer() != null) player.bukkitPlayer().closeInventory();
            player.setSpectator(false);
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
        if (startBorder != null) mainBorder = borderManager().newBorder(startBorder);
        chests.addAll(roundEvents.spawnChests());
        refreshAliveCounts();
        uiManager().refreshPlayerState();
        startCountdown(COUNTDOWN_TICKS, () -> startRound());
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
        GameRegion region = world().getRegion("spawn_platform");
        if (region != null) {
            world().fill(region, org.bukkit.Material.AIR);
            uiManager().playSound(playerManager().getPlayers(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.9f, 0.8f);
        }
    }

    void checkRoundEnd() {
        if (!roundActive || roundEnding || aliveTeams > 1) return;
        roundEnding = true;
        roundActive = false;
        cancelRoundTimer();
        borderManager().clear();
        mainBorder = null;
        timeManager().newTimer(ROUND_OVER_TITLE_DELAY).onFinish(timer -> {
            List<GamePlayer> winners = new ArrayList<>(playerManager().getNonSpectators());
            uiManager().title(playerManager().getSpectators(), Component.text("Round Over"));
            uiManager().playSound(playerManager().getSpectators(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
            uiManager().title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            uiManager().playSound(winners, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
            if (!winners.isEmpty()) {
                uiManager().subtitle(playerManager().getPlayers(), Component.text(
                    (winners.size() == 1 ? "Winner: " : "Winners: ") + 
                    winners.stream().map(player -> player.name()).collect(Collectors.joining(", "))
                ));
            }
            for (GamePlayer winner : winners) {
                winner.setSpectator(true, winner.location());
            }
        }).start();
        if (round >= maxRounds()) {
            timeManager().newTimer(GAME_END_DELAY).onFinish(timer -> {
                uiManager().gameMessage(playerManager().getPlayers(), Component.text("Game over!"));
                unload();
            }).start();
        } else {
            timeManager().newTimer(ROUND_RESTART_DELAY).onFinish(timer -> reload()).start();
        }
    }

    void refreshAliveCounts() {
        alivePlayers = 0;
        aliveTeams = 0;
        for (GameTeam team : teamManager().getTeams()) {
            int alive = aliveMembers(team);
            alivePlayers += alive;
            if (alive > 0) aliveTeams++;
        }
    }

    int aliveMembers(GameTeam team) {
        int alive = 0;
        for (GamePlayer player : team.getMembers()) if (!player.isSpectator()) alive++;
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
        return team == null ? 0 : Math.max(0, (aliveMembers(team) - 1) * config().getInt("base_respawn_time") * 20);
    }

    GameLocation respawnLocationFor(GamePlayer player) {
        GamePlayer teammate = player.closestTeammate(candidate -> !candidate.isSpectator());
        return teammate == null ? spawn() : teammate.location();
    }

    GameLocation spectatorLocationFor(GameLocation deathLocation) {
        if (deathLocation == null) return spawn();
        GameRegion startBorder = startingBorderRegion();
        return startBorder != null && !startBorder.contains(deathLocation) ? startBorder.center() : deathLocation;
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
        for (GameTeam team : teamManager().getTeams()) if (!team.getMembers().isEmpty()) teams++;
        return teams;
    }

    int roundElapsedSeconds() {
        return roundTimer == null ? 0 : (int) roundTimer.getElapsedSeconds();
    }

    @SuppressWarnings("null")
    void addKill(GamePlayer player) {
        kills.merge(player.uuid(), 1, Integer::sum);
    }

    int kills(GamePlayer player) {
        return kills.getOrDefault(player.uuid(), 0);
    }

    GameLocation spawn() {
        GameLocation spawn = world().getPoint("spawn");
        return spawn == null ? world().worldSpawn() : spawn;
    }

    GameRegion startingBorderRegion() {
        return world().getRegion("starting_border");
    }

    VoidWarsEvents roundEvents() {
        return roundEvents;
    }

    private void initializeGame() {
        teamManager().clear();
        players.assignTeams(config().getInt("team_size"));
        players.updateSpectatorTargets();
        roundSidebar.show();
        prepareRound();
    }

    private void startRound() {
        roundActive = true;
        for (GamePlayer player : playerManager().getPlayers()) {
            player.setSpectator(false);
            player.setGameMode(GameMode.SURVIVAL);
            player.clearEffects();
        }
        roundEvents.startRound();
    }

    private void restartRound() {
        for (GamePlayer player : playerManager().getPlayers()) {
            player.cancelRespawn();
            if (player.bukkitPlayer() != null) player.bukkitPlayer().closeInventory();
        }
        mapManager().setMap(config().getString("map")).thenRun(() -> prepareRound()).exceptionally(throwable -> {
            logError("Failed to restart round " + (round + 1) + ".", throwable);
            return null;
        });
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = null;
    }
}
