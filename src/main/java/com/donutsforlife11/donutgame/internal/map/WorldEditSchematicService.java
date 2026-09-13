package com.donutsforlife11.donutgame.internal.map;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;

import com.donutsforlife11.donutgame.api.map.MapRotation;
import com.donutsforlife11.donutgame.internal.map.WorldService.SchematicMetadata;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;

final class WorldEditSchematicService implements SchematicService {
    private final WorldService.SyncExecutor syncExecutor;
    private final Map<Path, CachedClipboard> clipboardCache = new ConcurrentHashMap<>();

    WorldEditSchematicService(WorldService.SyncExecutor syncExecutor) {
        this.syncExecutor = syncExecutor;
    }

    @Override
    public CompletableFuture<SchematicMetadata> paste(Path schematicFile, World world, Location minCorner, MapRotation rotation) {
        Objects.requireNonNull(schematicFile, "schematicFile");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(minCorner, "minCorner");
        Objects.requireNonNull(rotation, "rotation");
        CompletableFuture<SchematicMetadata> future = new CompletableFuture<>();
        syncExecutor.runSync(() -> {
            CachedClipboard cached = loadClipboard(schematicFile);
            ClipboardHolder holder = new ClipboardHolder(cached.clipboard());
            if (rotation != MapRotation.DEG_0) {
                holder.setTransform(holder.getTransform().combine(new AffineTransform().rotateY(rotation.degrees())));
            }
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operations.complete(holder.createPaste(editSession)
                    .to(adjustedPastePosition(minCorner, rotation, cached.metadata()))
                    .ignoreAirBlocks(false)
                    .build());
            }
            future.complete(cached.metadata());
        }, future);
        return future;
    }

    @Override
    public SchematicMetadata metadata(Path schematicFile) {
        return loadClipboard(schematicFile).metadata();
    }

    @Override
    public void clearCache() {
        clipboardCache.clear();
    }

    private BlockVector3 adjustedPastePosition(Location minCorner, MapRotation rotation, SchematicMetadata metadata) {
        int width = metadata.width();
        int depth = metadata.depth();
        int x = minCorner.getBlockX();
        int y = minCorner.getBlockY();
        int z = minCorner.getBlockZ();
        return switch (rotation) {
            case DEG_0 -> BlockVector3.at(x, y, z);
            case DEG_90 -> BlockVector3.at(x, y, z + width - 1);
            case DEG_180 -> BlockVector3.at(x + width - 1, y, z + depth - 1);
            case DEG_270 -> BlockVector3.at(x + depth - 1, y, z);
        };
    }

    private CachedClipboard loadClipboard(Path schematicFile) {
        try {
            Path normalizedPath = schematicFile.toAbsolutePath().normalize();
            long lastModified = Files.getLastModifiedTime(normalizedPath).toMillis();
            CachedClipboard cached = clipboardCache.get(normalizedPath);
            if (cached != null && cached.lastModified() == lastModified) {
                return cached;
            }
            var format = ClipboardFormats.findByPath(normalizedPath);
            if (format == null) {
                throw new IllegalStateException("Unknown schematic format for " + normalizedPath);
            }
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(normalizedPath.toFile()))) {
                clipboard = reader.read();
            }
            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();
            clipboard.setOrigin(min);
            CachedClipboard loaded = new CachedClipboard(
                lastModified,
                clipboard,
                new SchematicMetadata(max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1)
            );
            clipboardCache.put(normalizedPath, loaded);
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load schematic " + schematicFile + ".", exception);
        }
    }

    private record CachedClipboard(long lastModified, Clipboard clipboard, SchematicMetadata metadata) {
    }
}
