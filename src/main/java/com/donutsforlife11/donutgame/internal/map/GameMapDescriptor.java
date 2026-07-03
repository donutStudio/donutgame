package com.donutsforlife11.donutgame.internal.map;

import java.nio.file.Path;

public record GameMapDescriptor(
    String id,
    String name,
    Path mapPath,
    Path runtimeMapFolder,
    BackingType backingType,
    String assetName,
    Path runtimeAssetPath
) {
    public enum BackingType {
        NONE,
        SCHEMATIC,
        WORLD
    }
}