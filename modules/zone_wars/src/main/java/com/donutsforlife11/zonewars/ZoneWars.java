package com.donutsforlife11.zonewars;

import java.util.List;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class ZoneWars extends GameModule {
    private final String TEAM_COUNT = "team_count";
    private final String SPAWN = "spawn";
    private final String BORDER = "border";
    private final String HUNGER_ENABLED = "hunger_enabled";

    private ZoneWarsPlayers players;

    @Override
    public void onLoad() {
        this.players = new ZoneWarsPlayers(this);
        world().setSpawnLocation(world().getPoint(SPAWN));
        world().gamerules().pvp(false);
        world().setHungerEnabled(config().getBoolean(HUNGER_ENABLED));
        
        players.loadPlayers();
    }

    @Override
    public void onStart() {
        players.startPlayers();
        world().gamerules().pvp(true);
    }

    public int teamCount() {
        return config().getInt(TEAM_COUNT);
    }
    public List<GameLocation> spawns() {
        return world().getPoints(SPAWN);
    }
    public GameRegion borderRegion() {
        return world().getRegion(BORDER);
    }
}
