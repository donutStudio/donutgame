package com.donutsforlife11.lavarun;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.map.GameRegion;

public class LavaRunLevels {
    private final LavaRun game;

    private int currentLevel = 0;
    private double lavaSpeed = 0.01;

    public LavaRunLevels(LavaRun game) {
        this.game = game;
    }

    public GameMap selectSubmap() { // Will change this to an actual algorithm that selects an appropriate difficulty
        List<GameMap> submaps = game.map().submaps();
        int index = ThreadLocalRandom.current().nextInt(submaps.size());
        return submaps.get(index);
    }
    public void placeSubmap(GameMap submap) {
        for (GameRegion region : game.playableRegions()) {
            game.mapManager().placeMap(submap, region.min());
        }
    }
    public void setLavaSpeed() { // Will change this to an actual algorithm that selects an appropriate lava rise speed
        
    }

    public int currentLevel() {
        return currentLevel;
    }
    public double lavaSpeed() {
        return lavaSpeed;
    }
}
