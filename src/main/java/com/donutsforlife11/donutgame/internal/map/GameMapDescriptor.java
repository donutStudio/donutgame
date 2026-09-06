package com.donutsforlife11.donutgame.internal.map;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GameMapDescriptor(
    String id,
    String name,
    List<String> tags,
    Path mapPath,
    Path runtimeMapFolder,
    String metadataEntry,
    String metadataText,
    GameWorldDescriptor backingWorld,
    Map<String, GameMapDescriptor> submaps
) {
    public static final String DEFAULT_WORLD_ID = "default";

    public GameMapDescriptor {
        tags = List.copyOf(tags);
        submaps = Collections.unmodifiableMap(new LinkedHashMap<>(submaps));
    }

    public BackingType backingType() {
        return backingWorld == null ? BackingType.NONE : backingWorld.backingType();
    }

    public String assetName() {
        return backingWorld == null ? null : backingWorld.assetEntry();
    }

    public Path assetPath() {
        return backingWorld == null ? null : backingWorld.runtimeAssetPath();
    }

    public enum BackingType {
        NONE,
        SCHEMATIC,
        WORLD
    }

    public record GameWorldDescriptor(
        String id,
        BackingType backingType,
        String assetEntry,
        Path runtimeAssetPath
    ) {
    }
}
