package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsPlayers implements Listener {
    private final VoidWars game;

    public VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    public void assignTeams(int teamSize) {
        if (teamSize <= 0) {
            throw new IllegalArgumentException("Team size must be greater than zero!");
        }
        List<Player> players = new ArrayList<>(game.playerManager().getNonSpectators());
        int teamCount = (int) Math.ceil((double) players.size() / teamSize);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }
    public void setupPlayer(Player player) {
        Location spawnPoint = game.world().getPoints("spawn").get(0);
        game.playerManager().setPlayerSpawn(player, spawnPoint);
        player.teleportAsync(spawnPoint);
        if (game.started()) {
            game.playerManager().setSpectator(player);
        } else {
            game.playerManager().setNonSpectator(player);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!game.playerManager().isRegistered(player)) {
            return;
        }
        if (game.started()) {
            if (tryRespawn(player, game.config().getInt("base_respawn_time"))) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
        } else {
            game.playerManager().respawnPlayer(player);
        }
    }

    public boolean tryRespawn(Player player, int baseRespawnTime) {
        GameTeam team = game.teamManager().getPlayerTeam(player);
        int respawnTime = baseRespawnTime * (team.getMembers().size() - 1);

        if (isTeamEliminated(team)) {
            for (Player teammate : team.getMembers()) {
                game.playerManager().cancelRespawn(teammate);
                if (game.config().getInt("team_size") == 1) {
                    game.uiManager().title(teammate, Component.text("Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                } else {
                    game.uiManager().title(teammate, Component.text("Team Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                }
            }
        } else {
            game.playerManager().respawnPlayer(player, respawnTime * 20);
        }
        return !isTeamEliminated(team);
    }
    public boolean isTeamEliminated(GameTeam team) {
        for (Player player : team.getMembers()) {
            if (!game.playerManager().isSpectator(player)) {
                return false;
            }
        }
        return true;
    }
    public int alivePlayerCount() {
        return game.playerManager().getNonSpectators().size();
    }
    public int aliveTeamCount() {
        int aliveTeamCount = 0;
        for (GameTeam team : game.teamManager().getTeams()) {
            if (!isTeamEliminated(team)) {
                aliveTeamCount += 1;
            }
        }
        return aliveTeamCount;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!game.playerManager().isRegistered(player)) {
            return;
        }
        if (!game.started()) {
            event.setCancelled(true);
            return;
        }
    }
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!game.playerManager().isRegistered(player)) {
            return;
        }
        if (game.started()) {
            tryRespawn(player, game.config().getInt("base_respawn_time"));
        }
    }
}
