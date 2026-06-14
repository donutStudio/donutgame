package com.donutsforlife11.donutgame.api.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameMap {
    private final String id;
    private final String name;
    private final List<String> tags;
    private final Map<String, List<IntPoint>> points;
    private final Map<String, List<IntRegion>> regions;
    private final List<GameMap> submaps;
    private final BackingType backingType;
    private final Path schematicPath;
    private final Path slimePath;
    private final String mapPath;

    GameMap(
        String id,
        String name,
        Collection<String> tags,
        Map<String, List<IntPoint>> points,
        Map<String, List<IntRegion>> regions,
        Collection<GameMap> submaps,
        BackingType backingType,
        Path schematicPath,
        Path slimePath,
        String mapPath
    ) {
        this.id = id;
        this.name = name;
        this.tags = List.copyOf(tags);
        this.points = copyPoints(points);
        this.regions = copyRegions(regions);
        this.submaps = List.copyOf(submaps);
        this.backingType = backingType;
        this.schematicPath = schematicPath;
        this.slimePath = slimePath;
        this.mapPath = mapPath;
    }

    public static GameMap fromPath(String mapPath) {
        if (mapPath == null || mapPath.isBlank()) {
            throw new IllegalArgumentException("Map path cannot be blank.");
        }

        String normalizedPath = mapPath.replace('\\', '/');
        return new GameMap(
            normalizedPath,
            normalizedPath,
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            BackingType.NONE,
            null,
            null,
            normalizedPath
        );
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag);
    }

    public List<GameMap> getSubmaps(String... tags) {
        if (tags == null || tags.length == 0) {
            return submaps;
        }

        Set<String> requiredTags = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                requiredTags.add(tag);
            }
        }

        if (requiredTags.isEmpty()) {
            return submaps;
        }

        List<GameMap> matchingSubmaps = new ArrayList<>();
        for (GameMap submap : submaps) {
            boolean matches = true;

            for (String tag : requiredTags) {
                if (!submap.hasTag(tag)) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                matchingSubmaps.add(submap);
            }
        }

        return List.copyOf(matchingSubmaps);
    }

    BackingType backingType() {
        return backingType;
    }

    boolean isSchematicBacked() {
        return backingType == BackingType.SCHEMATIC;
    }

    boolean isSlimeBacked() {
        return backingType == BackingType.SLIME;
    }

    Path schematicPath() {
        return schematicPath;
    }

    Path slimePath() {
        return slimePath;
    }

    boolean isReference() {
        return mapPath != null;
    }

    String mapPath() {
        return mapPath;
    }

    Map<String, List<IntPoint>> points() {
        return points;
    }

    Map<String, List<IntRegion>> regions() {
        return regions;
    }

    private Map<String, List<IntPoint>> copyPoints(Map<String, List<IntPoint>> points) {
        Map<String, List<IntPoint>> copied = new LinkedHashMap<>();
        points.forEach((name, entries) -> copied.put(name, List.copyOf(entries)));
        return Map.copyOf(copied);
    }

    private Map<String, List<IntRegion>> copyRegions(Map<String, List<IntRegion>> regions) {
        Map<String, List<IntRegion>> copied = new LinkedHashMap<>();
        regions.forEach((name, entries) -> copied.put(name, List.copyOf(entries)));
        return Map.copyOf(copied);
    }

    enum BackingType {
        NONE,
        SCHEMATIC,
        SLIME
    }

    record IntPoint(int x, int y, int z) {
    }

    record IntRegion(IntPoint min, IntPoint max) {
    }
}
