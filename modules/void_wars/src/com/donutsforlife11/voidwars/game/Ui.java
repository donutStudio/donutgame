package com.donutsforlife11.voidwars.game;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.ui.GameSidebar;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.donutgame.api.ui.Values;
import com.donutsforlife11.voidwars.VoidWars;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public class Ui {
    private static VoidWars game;
    private static UIManager uiManager;
    private static PlayerManager playerManager;
    private static TeamManager teamManager;

    public static void init(VoidWars voidWars) {
        game = voidWars;
        uiManager = voidWars.uiManager();
        playerManager = voidWars.playerManager();
        teamManager = voidWars.teamManager();
    }

    public static GameSidebar sidebar() {
        GameSidebar sidebar = uiManager.createSidebar().setAudience(Audience.audience(playerManager.getPlayers()));

        sidebar.addLine(Values.fraction("Round", game::getRound, () -> game.config().getInt("max_rounds")));
        sidebar.addLine(Component.text(""));
        sidebar.addLine(Values.time("Next Event", 30 * 20));
        sidebar.addLine(Component.text(""));
        sidebar.addLine(Values.fraction("Alive Players", Players::alivePlayerCount, playerManager.getPlayers()::size));
        if (game.config().getInt("team_size") > 1) {
            sidebar.addLine(Values.fraction("Alive Teams", Players::aliveTeamCount, teamManager.getTeams()::size));
        }
        sidebar.addLine(Component.text(""));

        return sidebar;
    }
}
