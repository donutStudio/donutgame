package com.donutsforlife11.donutgame.api.map;

import java.util.List;
import java.util.Map;

public class GameMap {
    private final String id;
    private final String name;
    private final List<String> tags;
    private final Map<String, List<MapPoint>> points;
    private final Map<String, List<MapRegion>> regions;
    private final List<GameMap> submaps;

    public GameMap(
        String id,
        String name,
        List<String> tags,
        Map<String, List<MapPoint>> points,
        Map<String, List<MapRegion>> regions,
        List<GameMap> submaps
    ) {
        this.id = id;
        this.name = name;
        this.tags = List.copyOf(tags);
        this.points = Map.copyOf(points);
        this.regions = Map.copyOf(regions);
        this.submaps = List.copyOf(submaps);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<String> tags() {
        return tags;
    }

    public Map<String, List<MapPoint>> points() {
        return points;
    }

    public Map<String, List<MapRegion>> regions() {
        return regions;
    }

    public List<GameMap> submaps() {
        return submaps;
    }

    public record MapPoint(int x, int y, int z) {
    }

    public record MapRegion(MapPoint min, MapPoint max) {
    }
}