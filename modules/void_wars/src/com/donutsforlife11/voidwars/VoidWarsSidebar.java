package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;

public class VoidWarsSidebar {
    private final VoidWars game;
    private final GameSidebar sidebar;

    public VoidWarsSidebar(VoidWars game) {
        this.game = game;
        this.sidebar = game.uiManager().newSidebar();
    }

    public void createSidebar() {
        sidebar
            .addFraction("Round", () -> game.round(), game.maxRounds)
            .addLine()
            .addTime("Next Event", () -> game.voidWarsEvents().nextEventTime())
            .addLine();
        if (game.teamSize > 1) {
            sidebar.addFraction("Alive Teams", () -> game.teamManager().getNonSpectatorTeams().size(), game.teamManager().getTeams().size());
        }
        sidebar
            .addFraction("Alive Players", () -> game.playerManager().getNonSpectators().size(), game.playerManager().getPlayers().size())
            .addLine()
            .addInteger("Kills", player -> game.voidWarsPlayers().getPlayerKills(player));
        
        sidebar.setLabel("Next Event", () -> game.voidWarsEvents().nextEventLabel());
        sidebar.show();
    }
}
