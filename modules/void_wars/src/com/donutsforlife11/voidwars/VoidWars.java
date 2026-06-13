package com.donutsforlife11.voidwars;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;
import com.donutsforlife11.voidwars.game.EventSchedule;
import com.donutsforlife11.voidwars.game.Listeners;
import com.donutsforlife11.voidwars.game.Players;

public class VoidWars extends GameModule {
    public boolean gameStarted = false;

    @Override
    public void beforeLoad(GameContext context) {
        mapManager.setMap(GameMap.fromPath("void_wars/classic_enhanced.dmap"));
    }

    @Override
    public void onLoad(GameContext context) {
        initializeStatiClasses();
        playerManager.onPlayerEnteredWorld(player -> {
            Players.setupPlayer(player);
        });

        registerEvents(new Listeners(this));
        Players.assignTeams(config.getInt("team_size"));
    }

    @Override
    public void onStart() {
        gameStarted = true;
        for (Player player : playerManager.getNonSpectators()) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        EventSchedule.groundCollapseTimer(config.getInt("ground_collapse_time")).start();
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
    }

    public boolean gameStarted() {
        return gameStarted;
    }

    private void initializeStatiClasses() {
        Players.init(this);
        EventSchedule.init(this);
    }
}
