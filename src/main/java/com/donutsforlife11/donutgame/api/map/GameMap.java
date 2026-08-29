package com.donutsforlife11.donutgame.api.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameMap {
    private final String id;
    private final String name;
    private final List<String> tags;
    private final Map<String, List<MapPoint>> points;
    private final Map<String, List<MapRegion>> regions;
    private final Map<String, Object> metadata;
    private final List<GameMap> submaps;
    private final GameWorld world;
    private final GameLocation origin;
    private final MapRotation rotation;

    public GameMap(
        String id,
        String name,
        List<String> tags,
        Map<String, List<MapPoint>> points,
        Map<String, List<MapRegion>> regions,
        Map<String, Object> metadata,
        List<GameMap> submaps
    ) {
        this.id = id;
        this.name = name;
        this.tags = List.copyOf(tags);
        this.points = Map.copyOf(points);
        this.regions = Map.copyOf(regions);
        this.metadata = Map.copyOf(metadata);
        this.submaps = List.copyOf(submaps);
        this.world = null;
        this.origin = null;
        this.rotation = MapRotation.DEG_0;
    }

    private GameMap(
        String id,
        String name,
        List<String> tags,
        Map<String, List<MapPoint>> points,
        Map<String, List<MapRegion>> regions,
        Map<String, Object> metadata,
        List<GameMap> submaps,
        GameWorld world,
        GameLocation origin,
        MapRotation rotation
    ) {
        this.id = id;
        this.name = name;
        this.tags = List.copyOf(tags);
        this.points = Map.copyOf(points);
        this.regions = Map.copyOf(regions);
        this.metadata = Map.copyOf(metadata);
        this.submaps = List.copyOf(submaps);
        this.world = world;
        this.origin = origin;
        this.rotation = rotation == null ? MapRotation.DEG_0 : rotation;
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

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Object metadata(String key) {
        return metadata.get(key);
    }

    public Map<String, Object> customData() {
        return metadata;
    }

    public Object customData(String key) {
        return metadata.get(key);
    }

    public List<GameMap> submaps() {
        return submaps;
    }

    public boolean isPlaced() {
        return world != null;
    }

    public GameWorld world() {
        return world;
    }

    public GameLocation origin() {
        return origin;
    }

    public MapRotation rotation() {
        return rotation;
    }

    public List<GameLocation> getPoints(String pointName) {
        return points.getOrDefault(pointName, List.of()).stream()
            .map(point -> new GameLocation(point.x(), point.y(), point.z()))
            .toList();
    }

    public GameLocation getPoint(String pointName) {
        List<GameLocation> matches = getPoints(pointName);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public List<GameRegion> getRegions(String regionName) {
        return regions.getOrDefault(regionName, List.of()).stream()
            .map(region -> new GameRegion(
                new GameLocation(region.min().x(), region.min().y(), region.min().z()),
                new GameLocation(region.max().x(), region.max().y(), region.max().z())
            ))
            .toList();
    }

    public GameRegion getRegion(String regionName) {
        List<GameRegion> matches = getRegions(regionName);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public GameMap placed(GameWorld world, GameLocation origin, MapRotation rotation, com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata schematicMetadata) {
        Map<String, List<MapPoint>> placedPoints = new LinkedHashMap<>();
        points.forEach((name, values) -> placedPoints.put(name, values.stream()
            .map(point -> toMapPoint(transformPoint(origin, point, rotation, schematicMetadata)))
            .toList()));
        Map<String, List<MapRegion>> placedRegions = new LinkedHashMap<>();
        regions.forEach((name, values) -> placedRegions.put(name, values.stream()
            .map(region -> toMapRegion(transformRegion(origin, region, rotation, schematicMetadata)))
            .toList()));
        return new GameMap(id, name, tags, placedPoints, placedRegions, metadata, submaps, world, origin, rotation);
    }

    private GameLocation transformPoint(GameLocation origin, MapPoint point, MapRotation rotation, com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata schematicMetadata) {
        int width = schematicMetadata == null ? 0 : schematicMetadata.width();
        int depth = schematicMetadata == null ? 0 : schematicMetadata.depth();
        int x = point.x();
        int y = point.y();
        int z = point.z();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        return switch (rotation) {
            case DEG_0 -> new GameLocation(baseX + x, baseY + y, baseZ + z);
            case DEG_90 -> new GameLocation(baseX + z, baseY + y, baseZ - x + Math.max(0, width - 1));
            case DEG_180 -> new GameLocation(baseX - x + Math.max(0, width - 1), baseY + y, baseZ - z + Math.max(0, depth - 1));
            case DEG_270 -> new GameLocation(baseX - z + Math.max(0, depth - 1), baseY + y, baseZ + x);
        };
    }

    private GameRegion transformRegion(GameLocation origin, MapRegion region, MapRotation rotation, com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata schematicMetadata) {
        int[] xs = {region.min().x(), region.max().x()};
        int[] ys = {region.min().y(), region.max().y()};
        int[] zs = {region.min().z(), region.max().z()};
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int x : xs) {
            for (int z : zs) {
                GameLocation point = transformPoint(origin, new MapPoint(x, 0, z), rotation, schematicMetadata);
                minX = Math.min(minX, point.getBlockX());
                maxX = Math.max(maxX, point.getBlockX());
                minZ = Math.min(minZ, point.getBlockZ());
                maxZ = Math.max(maxZ, point.getBlockZ());
            }
        }
        return new GameRegion(
            new GameLocation(minX, origin.getBlockY() + Math.min(ys[0], ys[1]), minZ),
            new GameLocation(maxX, origin.getBlockY() + Math.max(ys[0], ys[1]), maxZ)
        );
    }

    private MapPoint toMapPoint(GameLocation location) {
        return new MapPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private MapRegion toMapRegion(GameRegion region) {
        return new MapRegion(toMapPoint(region.min()), toMapPoint(region.max()));
    }

    public record MapPoint(int x, int y, int z) {
    }

    public record MapRegion(MapPoint min, MapPoint max) {
    }
}
