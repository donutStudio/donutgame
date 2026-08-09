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
    }

    public void prepareRoundPlayers() {
        for (GamePlayer player : game.playerManager().getPlayers()) {
            setupPlayer(player);
            player.addEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1);
        }
    }

    public void handleRegisteredPlayer(GamePlayer player) {
        if (player.team() == null) {
            int teamSize = Math.max(1, game.config().getInt("team_size"));
            GameTeam team = smallestTeam();
            if (team == null || team.getMembers().size() >= teamSize) team = game.teamManager().newColoredTeam();
            team.addPlayer(player);
        }
        setupPlayer(player);
        game.refreshAliveCounts();
        game.checkRoundEnd();
    }

    public void handleUnregisteredPlayer(GamePlayer player) {
        GameTeam team = player.team();
        if (team != null) team.removePlayer(player);
        game.clearReconnectRespawn(player);
        game.refreshAliveCounts();
        if (team != null && game.teamEliminated(team)) game.cancelTeamRespawns(team);
        game.checkRoundEnd();
    }

    private void onDeath(GamePlayerEvent<PlayerDeathEvent> wrapped) {
        GamePlayer player = wrapped.player();
        GameLocation deathLocation = GameLocation.fromBukkit(wrapped.event().getPlayer().getLocation());
        player.setRespawnLocation(game.respawnPointAfterDeath(deathLocation));
        player.setSpectator(game.spectatorLocationFor(deathLocation));
        if (!game.roundActive()) {
            player.respawn(0, () -> game.spawn());
            return;
        }
        if (wrapped.event().getDamageSource().getCausingEntity() instanceof org.bukkit.entity.Player killerEntity) {
            GamePlayer killer = game.playerManager().getPlayer(killerEntity);
            if (killer != null && !killer.uuid().equals(player.uuid())) game.addKill(killer);
        }
        if (game.respawnPossible(player)) {
            wrapped.event().setKeepInventory(true);
            wrapped.event().setKeepLevel(true);
            wrapped.event().setDroppedExp(0);
            wrapped.event().getDrops().clear();
            player.respawn(game.respawnTicks(player), () -> game.respawnLocationFor(player));
        } else eliminateTeam(player.team());
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
            player.setSpectator(game.spectatorLocationFor(player.location()));
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
        player.cancelRespawn();
        player.setNonSpectator();
        player.setRespawnLocation(game.spawn());
        player.setGameMode(GameMode.ADVENTURE);
        player.clearInventory();
        player.clearExperience();
        player.clearEffects();
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setHealth(20);
        if (player.isOnline()) player.teleport(game.spawn());
    }

    private void eliminateTeam(GameTeam team) {
        if (team == null) return;
        game.cancelTeamRespawns(team);
        for (GamePlayer teammate : team.getMembers()) teammate.setSpectator(game.spectatorLocationFor(teammate.location()));
        game.refreshAliveCounts();
        game.checkRoundEnd();
        if (game.roundEnding()) return;
        for (GamePlayer teammate : team.getMembers()) game.uiManager().title(teammate, Component.text(game.config().getInt("team_size") == 1 ? "Eliminated!" : "Team Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
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
