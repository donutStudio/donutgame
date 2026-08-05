package com.donutsforlife11.voidwars;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.border.GameBorder;
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
    public void beforeLoad() {
        loadRound();
    }

    @Override
    public void onLoad() {
        registerEvents(voidWarsPlayers);
        voidWarsPlayers.assignTeams(config().getInt("team_size"));
        loadRound();
    }

    @Override
    public void onStart() {
        started = true;
    }

    @Override
    public void onUnload() {
        // playerManager().plugin().getLogger().info("tung, tung, tung, sahur");
    }

    public void loadRound() {
        mapManager().setMap(config().getString("map"));
        for (Player player : playerManager().getPlayers()) {
            voidWarsPlayers.setupPlayer(player);
        }
        mainBorder = borderManager().newBorder(world().getRegions("starting_border").get(0));
        round += 1;
    }
    public void startRound() {
        mainTimer = timeManager().newTimer();
    }
    public void endRound() {
        mainTimer.cancel();
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
