package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.ui.SidebarEntry;

final class VoidWarsSidebar {
    private final VoidWars game;
    private final VoidWarsEvents events;
    private final VoidWarsPlayers players;

    VoidWarsSidebar(VoidWars game, VoidWarsEvents events, VoidWarsPlayers players) {
        this.game = game;
        this.events = events;
        this.players = players;
    }

    void show() {
        SidebarEntry nextEvent = SidebarEntry.time("Next Event", events::nextEventTicks)
            .setLabel(events::nextEventLabel);
        var sidebar = game.uiManager().newSidebar()
            .addEntry(SidebarEntry.fraction("Round", game::round, () -> game.maxRounds))
            .addEntry(SidebarEntry.blank())
            .addEntry(nextEvent)
            .addEntry(SidebarEntry.blank());
        if (game.teamSize > 1) {
            sidebar.addEntry(SidebarEntry.fraction(
                "Alive Teams",
                () -> game.teamManager().getNonSpectatorTeams().size(),
                () -> game.teamManager().getTeams().size()
            ));
        }
        sidebar
            .addEntry(SidebarEntry.fraction(
                "Alive Players",
                () -> game.playerManager().getNonSpectators().size(),
                () -> game.playerManager().getPlayers().size()
            ))
            .addEntry(SidebarEntry.blank())
            .addEntry(SidebarEntry.integer("Kills", players::kills))
            .setRefreshInterval(10)
            .show();
    }
}
