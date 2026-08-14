package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.event.GamePlayerEvent;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsPlayers {
    private final VoidWars game;

    public VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    public void bind() {
        game.events().player(PlayerDeathEvent.class, event -> event.getPlayer(), wrapped -> onDeath(wrapped));
        game.events().player(PlayerJoinEvent.class, event -> event.getPlayer(), wrapped -> onJoin(wrapped));
        game.events().player(PlayerQuitEvent.class, event -> event.getPlayer(), wrapped -> onQuit(wrapped));
        game.events().player(PlayerMoveEvent.class, event -> event.getPlayer(), wrapped -> onMove(wrapped));
    }

    public void assignTeams(int teamSize) {
        if (teamSize <= 0) throw new IllegalArgumentException("Team size must be greater than zero.");
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        Collections.shuffle(players);
        int teamCount = Math.max(1, (int) Math.ceil(players.size() / (double) teamSize));
        for (int i = 0; i < teamCount; i++) game.teamManager().newColoredTeam();
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) teams.get(i % teamCount).addPlayer(players.get(i));
        updateSpectatorTargets();
    }

    public void updateSpectatorTargets() {
        if (game.config().getInt("team_size") <= 1) {
            for (GamePlayer player : game.playerManager().getPlayers()) {
                player.setSpectatablePlayers(game.playerManager().getPlayers());
                player.setSpectatableTeams(List.of());
            }
            return;
        }
        for (GamePlayer player : game.playerManager().getPlayers()) {
            player.setSpectatablePlayers(List.of());
            player.setSpectatableTeams(game.teamManager().getTeams());
        }
    }

    public void prepareRoundPlayers() {
        for (GamePlayer player : game.playerManager().getPlayers()) {
            setupPlayer(player);
            player.addEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true);
        }
    }

    public void handleRegisteredPlayer(GamePlayer player) {
        int teamSize = Math.max(1, game.config().getInt("team_size"));
        if (player.team() == null) {
            GameTeam team = smallestTeam();
            if (team == null || team.getMembers().size() >= teamSize) team = game.teamManager().newColoredTeam();
            team.addPlayer(player);
        }
        updateSpectatorTargets();
        setupPlayer(player);
        game.refreshAliveCounts();
        game.checkRoundEnd();
    }

    public void handleUnregisteredPlayer(GamePlayer player) {
        GameTeam team = player.team();
        if (team != null) team.removePlayer(player);
        game.clearReconnectRespawn(player);
        updateSpectatorTargets();
        game.refreshAliveCounts();
        if (team != null && game.teamEliminated(team)) game.cancelTeamRespawns(team);
        game.checkRoundEnd();
    }

    private void onDeath(GamePlayerEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.player();
        GameLocation deathLocation = GameLocation.fromBukkit(event.event().getPlayer().getLocation());
        GameLocation spectatorLocation = game.spectatorLocationFor(deathLocation);
        player.setSpawnPoint(game.respawnPointAfterDeath(deathLocation));
        event.event().setKeepInventory(true);
        event.event().setKeepLevel(true);
        event.event().setDroppedExp(0);
        event.event().getDrops().clear();
        if (!game.roundActive() || game.isTransitioning()) {
            game.plugin().getServer().getScheduler().runTask(game.plugin(), () -> {
                resetForRound(player, GameMode.ADVENTURE);
                player.addEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true);
            });
            return;
        }
        if (event.event().getDamageSource().getCausingEntity() instanceof org.bukkit.entity.Player killerEntity) {
            GamePlayer killer = game.playerManager().getPlayer(killerEntity);
            if (killer != null && !killer.uuid().equals(player.uuid())) game.addKill(killer);
        }
        if (game.respawnPossible(player)) {
            int respawnTicks = game.respawnTicks(player);
            game.plugin().getServer().getScheduler().runTask(game.plugin(), () -> {
                player.setSpectator(true, spectatorLocation);
                player.respawn(respawnTicks, () -> game.respawnLocationFor(player));
            });
        } else {
            game.plugin().getServer().getScheduler().runTask(game.plugin(), () -> eliminateTeam(player.team()));
        }
    }

    private void onJoin(GamePlayerEvent<PlayerJoinEvent> wrapped) {
        GamePlayer player = wrapped.player();
        if (!game.roundActive() || !game.needsReconnectRespawn(player)) return;
        game.clearReconnectRespawn(player);
        if (game.respawnPossible(player)) player.respawn(game.respawnTicks(player), () -> game.respawnLocationFor(player));
        else eliminateTeam(player.team());
    }

    private void onQuit(GamePlayerEvent<PlayerQuitEvent> wrapped) {
        if (!game.roundActive()) return;
        GamePlayer player = wrapped.player();
        player.cancelRespawn();
        if (!player.isSpectator()) {
            player.setSpectator(true, game.spectatorLocationFor(player.location()));
            game.markReconnectRespawn(player);
            GameTeam team = player.team();
            game.refreshAliveCounts();
            if (team != null && game.teamEliminated(team)) eliminateTeam(team);
            game.checkRoundEnd();
        } else if (player.team() != null && !game.teamEliminated(player.team())) game.markReconnectRespawn(player);
    }

    private void onMove(GamePlayerEvent<PlayerMoveEvent> wrapped) {
        if (game.roundActive()) return;
        GameRegion startBorder = game.startingBorderRegion();
        if (startBorder == null || wrapped.event().getTo() == null) return;
        if (!startBorder.contains(GameLocation.fromBukkit(wrapped.event().getTo()))) wrapped.player().teleport(game.spawn());
    }

    private void setupPlayer(GamePlayer player) {
        resetForRound(player, GameMode.ADVENTURE);
    }

    private void eliminateTeam(GameTeam team) {
        if (team == null) return;
        game.cancelTeamRespawns(team);
        for (GamePlayer teammate : team.getMembers()) teammate.setSpectator(true, game.spectatorLocationFor(teammate.location()));
        game.refreshAliveCounts();
        game.checkRoundEnd();
        if (game.roundEnding()) return;
        for (GamePlayer teammate : team.getMembers()) game.uiManager().title(teammate, Component.text(game.config().getInt("team_size") == 1 ? "Eliminated!" : "Team Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }

    private void resetForRound(GamePlayer player, GameMode gameMode) {
        player.cancelRespawn();
        player.setSpectator(false, game.spawn());
        player.setSpawnPoint(game.spawn());
        player.clearItems();
        player.clearEffects();
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.setHunger(20);
        player.setSaturation(20);
        player.heal();
        player.setGameMode(gameMode);
        player.teleport(game.spawn());
    }

    private GameTeam smallestTeam() {
        GameTeam selected = null;
        int size = Integer.MAX_VALUE;
        for (GameTeam team : game.teamManager().getTeams()) {
            int members = team.getMembers().size();
            if (members < size) {
                size = members;
                selected = team;
            }
        }
        return selected;
    }
}
