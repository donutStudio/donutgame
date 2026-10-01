package com.donutsforlife11.speedzone;

import com.donutsforlife11.donutgame.api.ui.SidebarEntry;

final class SpeedZoneSidebar {
    private final SpeedZone game;
    private final SpeedZonePlayers players;

    SpeedZoneSidebar(SpeedZone game, SpeedZonePlayers players) {
        this.game = game;
        this.players = players;
    }

    void show() {
        var sidebar = game.uiManager().newSidebar();
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
            .addEntry(SidebarEntry.integer("Lap", players::lap))
            .addEntry(SidebarEntry.fraction("Checkpoint", players::checkpoint, player -> game.checkpoints().size()))
            .setRefreshInterval(5)
            .show();
    }
}
