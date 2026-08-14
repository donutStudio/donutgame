package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;

final class VoidWarsSidebar {
    private final VoidWars game;
    private GameSidebar sidebar;

    VoidWarsSidebar(VoidWars game) {
        this.game = game;
    }

    void show() {
        sidebar = game.uiManager().newSidebar()
            .addFraction("Round", game::round, game::maxRounds)
            .addLine()
            .dynamicTime(() -> {
                VoidWarsEvents.SidebarEvent event = game.roundEvents().sidebarEvent();
                return event == null ? "Overtime" : event.label();
            }, () -> {
                VoidWarsEvents.SidebarEvent event = game.roundEvents().sidebarEvent();
                return event == null ? 0 : event.remainingSeconds();
            })
            .addLine()
            .addFraction("Alive Players", game::alivePlayers, game::totalPlayers);
        if (game.config().getInt("team_size") > 1) sidebar.addFraction("Alive Teams", game::aliveTeams, game::totalTeams);
        sidebar.addLine().addInteger("Kills", game::kills).show();
    }

    void clear() {
        if (sidebar != null) {
            sidebar.remove();
            sidebar = null;
        }
    }
}
