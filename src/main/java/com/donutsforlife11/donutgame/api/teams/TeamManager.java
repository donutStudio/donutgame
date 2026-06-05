package com.donutsforlife11.donutgame.api.teams;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

public class TeamManager {
    private final Scoreboard scoreboard;

    private final Map<String, GameTeam> teams = new HashMap<>();
    private final Map<UUID, GameTeam> playerTeams = new HashMap<>();

    public TeamManager() {
        scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager())
                .getNewScoreboard();
    }

    public GameTeam createTeam(String id, TeamProperties properties) {
        if (teams.containsKey(id)) {
            throw new IllegalStateException("Team " + id + " already exists!");
        }

        GameTeam team = new GameTeam(id, scoreboard, properties);
        teams.put(id, team);
        return team;
    }

    public GameTeam getTeam(String id) {
        return teams.get(id);
    }

    public GameTeam getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public boolean playerHasTeam(Player player) {
        return playerTeams.containsKey(player.getUniqueId());
    }

    public Collection<GameTeam> getTeams() {
        return teams.values();
    }

    public boolean join(Player player, String teamId) {
        GameTeam team = teams.get(teamId);

        if (team == null) {
            return false;
        }

        leave(player);

        team.addPlayer(player);
        playerTeams.put(player.getUniqueId(), team);

        player.setScoreboard(scoreboard);
        return true;
    }

    public boolean leave(Player player) {
        GameTeam team = playerTeams.remove(player.getUniqueId());

        if (team == null) {
            return false;
        }

        team.removePlayer(player);
        return true;
    }

    public boolean areTeammates(Player a, Player b) {
        GameTeam team = getPlayerTeam(a);
        return team != null && team == getPlayerTeam(b);
    }

    public boolean areEnemies(Player a, Player b) {
        return playerHasTeam(a)
                && playerHasTeam(b)
                && !areTeammates(a, b);
    }

    public void clear() {
        teams.values().forEach(GameTeam::unregister);

        teams.clear();
        playerTeams.clear();
    }
}