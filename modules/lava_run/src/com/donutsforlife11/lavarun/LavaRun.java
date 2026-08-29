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
    private static final String PLAY_AREA = "play_area";

    private int teamCount;
    private GameMap parentMap;
    private final List<GameMap> teamMaps = new ArrayList<>();

    @Override
    public void beforeLoad() {
        mapManager().setMap(MapManager.DEFAULT_MAP_ID);
        teamCount = Math.max(1, config().getInt("team_count", 1));
        parentMap = chooseParentMap();
    }

    @Override
    public void onLoad() {
        teamMaps.clear();
        if (parentMap == null) {
            logWarning("No Lava Run parent map configured.");
            return;
        }
        GameMap.MapRegion rawPlayArea = parentMap.regions().getOrDefault(PLAY_AREA, List.of()).stream().findFirst().orElse(null);
        if (rawPlayArea == null) {
            throw new IllegalStateException("Lava Run parent map " + parentMap.id() + " is missing region " + PLAY_AREA + ".");
        }
        int spacing = Math.abs(rawPlayArea.max().x() - rawPlayArea.min().x()) + 16;
        for (int teamIndex = 0; teamIndex < teamCount; teamIndex++) {
            GameMap placement = mapManager().placeMap(parentMap, new GameLocation(teamIndex * spacing, 0, 0)).join();
            teamMaps.add(placement);
        }
        world().setHungerEnabled(false);
    }

    public GameMap parentMap() {
        return parentMap;
    }

    public List<GameMap> teamMaps() {
        return List.copyOf(teamMaps);
    }

    public List<GameRegion> playAreas() {
        return teamMaps.stream()
            .map(placement -> placement.getRegion(PLAY_AREA))
            .filter(region -> region != null)
            .toList();
    }

    private GameMap chooseParentMap() {
        List<String> maps = config().getStringList("maps");
        if (maps.isEmpty()) return null;
        return mapManager().getMapFromId(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
    }
}
