package com.donutsforlife11.donutgame.internal.map;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.World;

import com.donutsforlife11.donutgame.api.map.MapRotation;
import com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata;

interface SchematicService {
    CompletableFuture<SchematicMetadata> paste(Path schematicFile, World world, Location minCorner, MapRotation rotation);

    SchematicMetadata metadata(Path schematicFile);

    void clearCache();
}
