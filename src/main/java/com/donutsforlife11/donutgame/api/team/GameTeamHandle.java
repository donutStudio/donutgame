package com.donutsforlife11.donutgame.api.team;

import org.bukkit.scoreboard.Team;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should use {@link GameTeam}'s scoped API instead.
 */
public final class GameTeamHandle {
    private GameTeamHandle() {
    }

    public static Team bukkitTeam(GameTeam team) {
        return team == null ? null : team.bukkitTeam();
    }
}
