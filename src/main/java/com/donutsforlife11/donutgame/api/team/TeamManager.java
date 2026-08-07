package com.donutsforlife11.donutgame.api.team;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

import net.kyori.adventure.text.format.NamedTextColor;

public class TeamManager {
    private final Scoreboard scoreboard;
    private final PlayerManager playerManager;

    private final Set<GameTeam> teams = new HashSet<>();

    private final List<NamedTextColor> colorAssignmentList =
        List.of(NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.AQUA, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_RED, NamedTextColor.DARK_AQUA, NamedTextColor.DARK_BLUE, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY, NamedTextColor.BLACK, NamedTextColor.WHITE);
    private int colorAssignmentIndex = 0;

    public TeamManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
        scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
    }

    public GameTeam newTeam() {
        GameTeam team = new GameTeam(this, scoreboard);
        teams.add(team);
        return team;
    }

    public GameTeam newColoredTeam() {
        GameTeam team = newTeam().setColor(colorAssignmentList.get(colorAssignmentIndex));
        colorAssignmentIndex = colorAssignmentIndex >= colorAssignmentList.size() - 1 ? 0 : colorAssignmentIndex + 1;
        return team;
    }

    public Collection<GameTeam> getTeams() {
        return Collections.unmodifiableSet(teams);
    }

    public GameTeam getPlayerTeam(GamePlayer player) {
        for (GameTeam team : teams) {
            if (team.getMembers().contains(player)) {
                return team;
            }
        }
        return null;
    }

    public boolean playerHasTeam(GamePlayer player) {
        return getPlayerTeam(player) != null;
    }

    Collection<GameTeam> getTeamsModifiable() {
        return teams;
    }

    PlayerManager playerManager() {
        return playerManager;
    }

    public void shutdown() {
        for (GameTeam team : Set.copyOf(teams)) {
            team.remove();
        }
    }
}
