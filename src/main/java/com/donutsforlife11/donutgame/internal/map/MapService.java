package com.donutsforlife11.donutgame.internal.map;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.internal.file.FileService;

public class MapService {
    private final FileService fileService;

    public MapService(FileService fileService) {
        this.fileService = fileService;
    }

    public GameMapDescriptor getGameMapDescriptor(String id) {
        return fileService.getGameMapDescriptor(id);
    }

    public GameMap load(String id) {
        return loadGameMap(getGameMapDescriptor(id));
    }

    public GameMap loadGameMap(GameMapDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        try (InputStream inputStream = Files.newInputStream(descriptor.mapPath())) {
            return readGameMap(
                inputStream,
                descriptor.mapPath().getFileName().toString(),
                descriptor.runtimeMapFolder(),
                descriptor.assetName(),
                descriptor.assetPath(),
                descriptor.backingType()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load map " + descriptor.id() + ".", exception);
        }
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

    private GameMap readGameMap(
        InputStream inputStream,
        String sourceName,
        Path runtimeMapFolder,
        String assetName,
        Path assetPath,
        GameMapDescriptor.BackingType backingType
    ) throws IOException {
        Files.createDirectories(runtimeMapFolder);
        byte[] metadataBytes = null;
        Map<String, GameMap> submaps = new LinkedHashMap<>();
        String discoveredAssetName = assetName;
        Path discoveredAssetPath = assetPath;
        GameMapDescriptor.BackingType discoveredBackingType = backingType == null ? GameMapDescriptor.BackingType.NONE : backingType;
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = fileService.normalizeFileName(entry.getName());
                byte[] entryBytes = zip.readAllBytes();
                if (entryName.equals("map.yml")) {
                    metadataBytes = entryBytes;
                    continue;
                }
                if (!entryName.contains("/") && isMapAsset(entryName) && discoveredAssetName == null) {
                    discoveredAssetName = entryName;
                    discoveredAssetPath = runtimeMapFolder.resolve(entryName).normalize();
                    discoveredBackingType = backingType(entryName);
                }
                if (discoveredAssetName != null && entryName.equals(discoveredAssetName)) {
                    if (discoveredAssetPath == null) {
                        throw new IllegalStateException("Map " + sourceName + " has an asset name but no runtime asset path.");
                    }
                    Files.createDirectories(discoveredAssetPath.getParent());
                    Files.write(discoveredAssetPath, entryBytes);
                    continue;
                }
                if (entryName.startsWith("submaps/") && entryName.endsWith(".dmap")) {
                    String submapName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    Path submapRuntimeFolder = runtimeMapFolder
                        .resolve("submaps")
                        .resolve(stripExtension(sanitizeFileName(submapName)))
                        .normalize();
                    GameMap submap = readGameMap(
                        new ByteArrayInputStream(entryBytes),
                        sourceName + ":" + submapName,
                        submapRuntimeFolder,
                        null,
                        null,
                        GameMapDescriptor.BackingType.NONE
                    );
                    submaps.put(submap.id(), submap);
                }
            }
        }
        if (metadataBytes == null) {
            throw new IllegalStateException("Map " + sourceName + " is missing map.yml.");
        }

        YamlConfiguration metadata = fileService.loadYaml(metadataBytes);
        String id = metadata.getString("id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Map " + sourceName + " is missing an id.");
        }
        return new GameMap(
            id,
            metadata.getString("name", id),
            metadata.getStringList("tags"),
            readPoints(metadata.getConfigurationSection("points")),
            readRegions(metadata.getConfigurationSection("regions")),
            readCustomMetadata(metadata),
            submaps,
            discoveredBackingType,
            discoveredAssetPath
        );
    }

    private boolean isMapAsset(String entryName) {
        String lowerName = entryName.toLowerCase(java.util.Locale.ROOT);
        return lowerName.endsWith(".slime") || lowerName.endsWith(".schem");
    }

    private GameMapDescriptor.BackingType backingType(String entryName) {
        String lowerName = entryName.toLowerCase(java.util.Locale.ROOT);
        if (lowerName.endsWith(".slime")) {
            return GameMapDescriptor.BackingType.WORLD;
        }
        if (lowerName.endsWith(".schem")) {
            return GameMapDescriptor.BackingType.SCHEMATIC;
        }
        return GameMapDescriptor.BackingType.NONE;
    }

    private Map<String, List<GameMap.MapPoint>> readPoints(ConfigurationSection section) {
        Map<String, List<GameMap.MapPoint>> points = new HashMap<>();
        if (section == null) {
            return points;
        }
        for (String pointName : section.getKeys(false)) {
            List<?> rawPoints = section.getList(pointName);
            if (rawPoints == null) {
                continue;
            }
            List<GameMap.MapPoint> entries = new ArrayList<>();
            for (Object rawPoint : rawPoints) {
                if (!(rawPoint instanceof Map<?, ?> pointMap)) {
                    continue;
                }
                entries.add(new GameMap.MapPoint(
                    readRequiredInt(pointMap, "x", pointName),
                    readRequiredInt(pointMap, "y", pointName),
                    readRequiredInt(pointMap, "z", pointName)
                ));
            }
            points.put(pointName, List.copyOf(entries));
        }
        return Map.copyOf(points);
    }

    private Map<String, List<GameMap.MapRegion>> readRegions(ConfigurationSection section) {
        Map<String, List<GameMap.MapRegion>> regions = new HashMap<>();
        if (section == null) {
            return regions;
        }
        for (String regionName : section.getKeys(false)) {
            List<?> rawRegions = section.getList(regionName);
            if (rawRegions == null) {
                continue;
            }
            List<GameMap.MapRegion> entries = new ArrayList<>();
            for (Object rawRegion : rawRegions) {
                if (!(rawRegion instanceof Map<?, ?> regionMap)) {
                    continue;
                }
                Map<?, ?> min = asMap(regionMap.get("min"), regionName, "min");
                Map<?, ?> max = asMap(regionMap.get("max"), regionName, "max");
                entries.add(new GameMap.MapRegion(
                    new GameMap.MapPoint(
                        readRequiredInt(min, "x", regionName),
                        readRequiredInt(min, "y", regionName),
                        readRequiredInt(min, "z", regionName)
                    ),
                    new GameMap.MapPoint(
                        readRequiredInt(max, "x", regionName),
                        readRequiredInt(max, "y", regionName),
                        readRequiredInt(max, "z", regionName)
                    )
                ));
            }
            regions.put(regionName, List.copyOf(entries));
        }
        return Map.copyOf(regions);
    }

    private Map<String, Object> readCustomMetadata(YamlConfiguration metadata) {
        Map<String, Object> custom = new HashMap<>();
        for (String key : metadata.getKeys(false)) {
            if (key.equals("id") || key.equals("name") || key.equals("tags") || key.equals("points") || key.equals("regions")) {
                continue;
            }
            custom.put(key, metadata.get(key));
        }
        return Map.copyOf(custom);
    }

    private Map<?, ?> asMap(Object value, String entryName, String keyName) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException("Entry " + entryName + " is missing " + keyName + ".");
    }

    private int readRequiredInt(Map<?, ?> map, String key, String entryName) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("Entry " + entryName + " is missing " + key + ".");
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Entry " + entryName + " has a non-integer " + key + ".", exception);
        }
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
