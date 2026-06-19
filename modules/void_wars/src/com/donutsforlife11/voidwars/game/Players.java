package com.donutsforlife11.voidwars.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.teams.GameTeam;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.voidwars.VoidWars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Players {
    private static VoidWars game;
    private static PlayerManager playerManager;
    private static TeamManager teamManager;
    private static UIManager uiManager;

    public static void init(VoidWars voidWars) {
        game = voidWars;
        playerManager = voidWars.playerManager();
        teamManager = voidWars.teamManager();
        uiManager = voidWars.uiManager();
    }

    public static void setupPlayer(Player player) {
        Location spawnPoint = game.world().getPoints("spawn").getFirst();
        player.teleportAsync(spawnPoint);
        playerManager.setPlayerSpawn(player, spawnPoint);
        if (game.gameStarted()) {
            playerManager.setSpectator(player);
        } else {
            playerManager.setNonSpectator(player);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    public static void assignTeams(int teamSize) {
        if (teamSize <= 0) {
            throw new IllegalArgumentException("Team size must be greater than zero!");
        }
        List<Player> players = new ArrayList<>(playerManager.getNonSpectators());
        int teamCount = (int) Math.ceil((double) players.size() / teamSize);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            teamManager.createTeam(game.gameId() + "." + i);
        }
        List<GameTeam> teams = new ArrayList<>(teamManager.getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }

    public static boolean isTeamEliminated(GameTeam team) {
        for (Player player : team.getMembers()) {
            if (!playerManager.isSpectator(player)) {
                return false;
            }
        }
        return true;
    }

    public static void tryRespawn(Player player, int baseRespawnTime) {
        GameTeam team = teamManager.getPlayerTeam(player);
        int respawnTime = baseRespawnTime * (team.getSize() - 1);

        if (isTeamEliminated(team)) {
            for (Player teammate : team.getMembers()) {
                playerManager.cancelRespawn(teammate);
                if (game.config().getInt("team_size") == 1) {
                    uiManager.title(teammate, Component.text("Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                } else {
                    uiManager.title(teammate, Component.text("Team Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                }
            }
        } else {
            playerManager.respawnPlayer(player, respawnTime * 20);
        }
    }

    public static int alivePlayerCount() {
        return playerManager.getNonSpectators().size();
    }

    public static int aliveTeamCount() {
        int aliveTeamCount = 0;
        for (GameTeam team : teamManager.getTeams()) {
            if (!isTeamEliminated(team)) {
                aliveTeamCount += 1;
            }
        }
        return aliveTeamCount;
    }
}
