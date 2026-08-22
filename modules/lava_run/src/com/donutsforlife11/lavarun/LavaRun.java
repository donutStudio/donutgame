package com.donutsforlife11.lavarun;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class LavaRun extends GameModule {
    
    public int teamCount;
    public String mapCorners = "map_corners";
    public String playableArea = "playable_area";

    private LavaRunPlayers lavaRunPlayers;
    private GameMap map;
    // private final List<GameLocation> mapLocations = new ArrayList<>();
    private final List<GameRegion> playableRegions = new ArrayList<>();

    @Override
    public void beforeLoad() {
        mapManager().setMap(MapManager.DEFAULT_MAP_ID);
        teamCount = config().getInt("team_count");
        List<String> maps = config().getStringList("maps");
        if (maps.isEmpty()) {
            return;
        }
        String mapId = maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
        map = mapManager().getMapFromId(mapId);

        int placementStep = (int) world().getRegion(mapCorners).sizeX() + 1;
        for (int i = 0; i < teamCount; i++) {
            GameLocation location = new GameLocation(i * placementStep, 0, 0);
            // mapLocations.add(location);
            mapManager().placeMap(map, location);
            playableRegions.addAll(world().getRegions(playableArea));
        }
    }

    @Override
    public void onLoad() {
        lavaRunPlayers = new LavaRunPlayers(this);

        world().setHungerEnabled(false);
        lavaRunPlayers.assignTeams();
    }

    public GameMap map() {
        return map;
    }
    public List<GameRegion> playableRegions() {
        return playableRegions;
    }
}
