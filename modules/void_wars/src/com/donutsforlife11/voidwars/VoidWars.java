package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class VoidWars extends GameModule {
    private final VoidWarsPlayers voidWarsPlayers = new VoidWarsPlayers(this);
    private final VoidWarsEvents voidWarsEvents = new VoidWarsEvents(this);

    private boolean started = false;
    private int round = 0;
    private GameTimer mainTimer;
    private GameBorder mainBorder;

    @Override
    public void onLoad() {
        registerEvents(voidWarsPlayers);
        mapManager().setMap(config().getString("map")).thenRun(() -> {
            voidWarsPlayers.assignTeams(config().getInt("team_size"));
            loadRound();
        });
    }

    @Override
    public void onStart() {
        started = true;
        startRound();
    }

    public void loadRound() {
        for (GamePlayer player : playerManager().getPlayers()) {
            voidWarsPlayers.setupPlayer(player);
        }
        if (!world().getRegions("starting_border").isEmpty()) {
            mainBorder = borderManager().newBorder(world().getRegions("starting_border").get(0));
        }
        round += 1;
    }

    public void startRound() {
        mainTimer = timeManager().newTimer();
        voidWarsEvents.bind(mainTimer);
        mainTimer.start();
    }

    public void endRound() {
        if (mainTimer != null) {
            mainTimer.cancel();
        }
    }

    public boolean started() {
        return started;
    }

    public int round() {
        return round;
    }

    public GameTimer mainTimer() {
        return mainTimer;
    }

    public GameBorder mainBorder() {
        return mainBorder;
    }
}
