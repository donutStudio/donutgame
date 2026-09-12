package com.donutsforlife11.donutgame.api.map;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.WorldService;

public class GameMap {
    private final String id;
    private final String name;
    private final List<String> tags;
    private final Map<String, List<MapPoint>> points;
    private final Map<String, List<MapRegion>> regions;
    private final Map<String, Object> metadata;
    private final Map<String, GameMap> submaps;
    private final BackingType backingType;
    private final Path assetPath;
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
        Map<String, GameMap> submaps
    ) {
        this(id, name, tags, points, regions, metadata, submaps, BackingType.NONE, null);
    }

    public GameMap(
        String id,
        String name,
        List<String> tags,
        Map<String, List<MapPoint>> points,
        Map<String, List<MapRegion>> regions,
        Map<String, Object> metadata,
        Map<String, GameMap> submaps,
        BackingType backingType,
        Path assetPath
    ) {
        this(id, name, tags, points, regions, metadata, submaps, backingType, assetPath, null, null, MapRotation.DEG_0);
    }

    private GameMap(
        String id,
        String name,
        List<String> tags,
        Map<String, List<MapPoint>> points,
        Map<String, List<MapRegion>> regions,
        Map<String, Object> metadata,
        Map<String, GameMap> submaps,
        BackingType backingType,
        Path assetPath,
        GameWorld world,
        GameLocation origin,
        MapRotation rotation
    ) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.tags = List.copyOf(tags);
        this.points = deepCopyPoints(points);
        this.regions = deepCopyRegions(regions);
        this.metadata = Map.copyOf(metadata);
        this.submaps = Map.copyOf(submaps);
        this.backingType = backingType == null ? BackingType.NONE : backingType;
        this.assetPath = assetPath;
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

    Map<String, List<MapPoint>> points() {
        return points;
    }

    Map<String, List<MapRegion>> regions() {
        return regions;
    }

    public Map<String, List<GameLocation>> getPoints() {
        Map<String, List<GameLocation>> copy = new LinkedHashMap<>();
        points.keySet().forEach(name -> copy.put(name, getPoints(name)));
        return Map.copyOf(copy);
    }

    public Map<String, List<GameRegion>> getRegions() {
        Map<String, List<GameRegion>> copy = new LinkedHashMap<>();
        regions.keySet().forEach(name -> copy.put(name, getRegions(name)));
        return Map.copyOf(copy);
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Object metadata(String key) {
        return metadata.get(key);
    }

    public Map<String, GameMap> submaps() {
        return submaps;
    }

    private GameMap submap(String id) {
        return submaps.get(id);
    }

    public GameMap getSubmap(String id) {
        return submap(id);
    }

    BackingType backingType() {
        return backingType;
    }

    Path assetPath() {
        return assetPath;
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
            .map(point -> bind(new GameLocation(point.x(), point.y(), point.z())))
            .toList();
    }

    public GameLocation getPoint(String pointName) {
        List<GameLocation> matches = getPoints(pointName);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public List<GameRegion> getRegions(String regionName) {
        return regions.getOrDefault(regionName, List.of()).stream()
            .map(region -> new GameRegion(
                bind(new GameLocation(region.min().x(), region.min().y(), region.min().z())),
                bind(new GameLocation(region.max().x(), region.max().y(), region.max().z()))
            ))
            .toList();
    }

    public GameRegion getRegion(String regionName) {
        List<GameRegion> matches = getRegions(regionName);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public GameMap placed(GameWorld world, GameLocation origin, MapRotation rotation, WorldService.SchematicMetadata schematicMetadata) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(origin, "origin");
        MapRotation actualRotation = rotation == null ? MapRotation.DEG_0 : rotation;
        Map<String, List<MapPoint>> placedPoints = new LinkedHashMap<>();
        points.forEach((name, values) -> placedPoints.put(name, values.stream()
            .map(point -> toMapPoint(transformPoint(origin, point, actualRotation, schematicMetadata)))
            .toList()));
        Map<String, List<MapRegion>> placedRegions = new LinkedHashMap<>();
        regions.forEach((name, values) -> placedRegions.put(name, values.stream()
            .map(region -> toMapRegion(transformRegion(origin, region, actualRotation, schematicMetadata)))
            .toList()));
        return new GameMap(id, name, tags, placedPoints, placedRegions, metadata, submaps, backingType, assetPath, world, origin.inWorld(world), actualRotation);
    }

    private GameLocation bind(GameLocation location) {
        return world == null ? location : location.inWorld(world);
    }

    private GameLocation transformPoint(GameLocation origin, MapPoint point, MapRotation rotation, WorldService.SchematicMetadata schematicMetadata) {
        int width = schematicMetadata == null ? 0 : schematicMetadata.width();
        int depth = schematicMetadata == null ? 0 : schematicMetadata.depth();
        int x = point.x();
        int y = point.y();
        int z = point.z();
        int baseX = origin.blockX();
        int baseY = origin.blockY();
        int baseZ = origin.blockZ();
        return switch (rotation) {
            case DEG_0 -> new GameLocation(baseX + x, baseY + y, baseZ + z);
            case DEG_90 -> new GameLocation(baseX + z, baseY + y, baseZ - x + Math.max(0, width - 1));
            case DEG_180 -> new GameLocation(baseX - x + Math.max(0, width - 1), baseY + y, baseZ - z + Math.max(0, depth - 1));
            case DEG_270 -> new GameLocation(baseX - z + Math.max(0, depth - 1), baseY + y, baseZ + x);
        };
    }

    private GameRegion transformRegion(GameLocation origin, MapRegion region, MapRotation rotation, WorldService.SchematicMetadata schematicMetadata) {
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
                minX = Math.min(minX, point.blockX());
                maxX = Math.max(maxX, point.blockX());
                minZ = Math.min(minZ, point.blockZ());
                maxZ = Math.max(maxZ, point.blockZ());
            }
        }
        return new GameRegion(
            new GameLocation(minX, origin.blockY() + Math.min(ys[0], ys[1]), minZ),
            new GameLocation(maxX, origin.blockY() + Math.max(ys[0], ys[1]), maxZ)
        );
    }

    private MapPoint toMapPoint(GameLocation location) {
        return new MapPoint(location.blockX(), location.blockY(), location.blockZ());
    }

    private MapRegion toMapRegion(GameRegion region) {
        return new MapRegion(toMapPoint(region.min()), toMapPoint(region.max()));
    }

    private static Map<String, List<MapPoint>> deepCopyPoints(Map<String, List<MapPoint>> source) {
        Map<String, List<MapPoint>> copy = new LinkedHashMap<>();
        source.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    private static Map<String, List<MapRegion>> deepCopyRegions(Map<String, List<MapRegion>> source) {
        Map<String, List<MapRegion>> copy = new LinkedHashMap<>();
        source.forEach((name, entries) -> copy.put(name, List.copyOf(entries)));
        return Map.copyOf(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    public record MapPoint(int x, int y, int z) {
    }

    public record MapRegion(MapPoint min, MapPoint max) {
    }
}
