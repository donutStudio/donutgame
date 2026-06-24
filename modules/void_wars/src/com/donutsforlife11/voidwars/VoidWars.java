package com.donutsforlife11.voidwars;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.ui.GameSidebar;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;
import com.donutsforlife11.voidwars.game.EventSchedule;
import com.donutsforlife11.voidwars.game.Listeners;
import com.donutsforlife11.voidwars.game.Players;
import com.donutsforlife11.voidwars.game.Ui;

public class VoidWars extends GameModule {
    private boolean gameStarted = false;
    private int round = 0;
    private GameSidebar sidebar;

    @Override
    public void beforeLoad(GameContext context) {
        mapManager.setMap(GameMap.fromPath("void_wars/classic_enhanced.dmap"));
    }

    @Override
    public void onLoad(GameContext context) {
        initializeStaticClasses();
        playerManager.onPlayerEnteredWorld(player -> {
            Players.setupPlayer(player);
        });

        registerEvents(new Listeners(this));
        Players.assignTeams(config.getInt("team_size"));
    }

    @Override
    public void onStart() {
        round += 1;
        gameStarted = true;
        for (Player player : playerManager.getNonSpectators()) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        EventSchedule.groundCollapseTimer(config.getInt("ground_collapse_time")).start();
        EventSchedule.spawnChests(world.getPoints("chest"));
        sidebar = Ui.sidebar().setVisibility(true);
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
    }

    public boolean gameStarted() {
        return gameStarted;
    }

    private void initializeStaticClasses() {
        Players.init(this);
        EventSchedule.init(this);
        Ui.init(this);
    }

    public int getRound() {
        return round;
    }

    public GameSidebar getSidebar() {
        return sidebar;
    }
}
