package com.donutsforlife11.donutgame.internal.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.donutsforlife11.donutgame.internal.file.FileService;

public class MapService {
    private final FileService fileService;

    public MapService(FileService fileService) {
        this.fileService = fileService;
    }

    public GameMapDescriptor getGameMapDescriptor(String id) {
        return fileService.getGameMapDescriptor(id);
    }

    public void extractBackingAsset(GameMapDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String assetName = descriptor.assetName();
        Path assetPath = descriptor.assetPath();
        if (assetName == null || assetPath == null) {
            throw new IllegalStateException("Map " + descriptor.id() + " does not have a backing asset.");
        }

        try {
            Files.createDirectories(assetPath.getParent());
            try (InputStream input = Files.newInputStream(descriptor.mapPath());
                 ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (fileService.normalizeFileName(entry.getName()).equals(assetName)) {
                        Files.copy(zip, assetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return;
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to extract backing asset for map " + descriptor.id() + ".", exception);
        }

        throw new IllegalStateException("Map " + descriptor.id() + " is missing backing asset " + assetName + ".");
    }
}
