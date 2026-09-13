package com.donutsforlife11.donutgame.internal.map;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.World;

import com.donutsforlife11.donutgame.api.map.MapRotation;
import com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata;

final class UnavailableSchematicService implements SchematicService {
    private final Throwable cause;

    UnavailableSchematicService(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public CompletableFuture<SchematicMetadata> paste(Path schematicFile, World world, Location minCorner, MapRotation rotation) {
        return CompletableFuture.failedFuture(exception());
    }

    @Override
    public SchematicMetadata metadata(Path schematicFile) {
        throw exception();
    }

    @Override
    public void clearCache() {
    }

    private IllegalStateException exception() {
        return new IllegalStateException(
            "Schematic support is unavailable. Install a compatible FastAsyncWorldEdit/WorldEdit version, or use world-backed maps only.",
            cause
        );
    }
}
