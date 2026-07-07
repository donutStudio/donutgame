package com.donutsforlife11.donutgame.internal.map;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.internal.file.FileRegistry;

public class MapRegistry {
    private final File runtimeFolder;
    private final FileRegistry fileRegistry;

    public MapRegistry(FileRegistry fileRegistry) {
        this.fileRegistry = fileRegistry;
        runtimeFolder = fileRegistry.runtimeFolder();
    }

    public GameMap loadGameMap(String id) {
        return loadGameMap(fileRegistry.getGameMapDescriptor(id));
    }

    public GameMap loadGameMap(GameMapDescriptor descriptor) {
        return readGameMap(descriptor);
    }

    public GameMapDescriptor getGameMapDescriptor(String id) {
        return fileRegistry.getGameMapDescriptor(id);
    }

    public FileRegistry fileRegistry() {
        return fileRegistry;
    }

    private GameMap readGameMap(GameMapDescriptor descriptor) {
        try (InputStream inputStream = Files.newInputStream(descriptor.mapPath())) {
            return readGameMap(
                inputStream,
                descriptor.mapPath().getFileName().toString(),
                descriptor.runtimeMapFolder(),
                descriptor.assetName(),
                descriptor.assetPath()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load map " + descriptor.mapPath(), e);
        }
    }

    public File runtimeFolder() {
        return runtimeFolder;
    }

    private GameMap readGameMap(InputStream inputStream, String sourceName, Path runtimeMapFolder, String assetName, Path runtimeAssetPath) throws IOException {
        Files.createDirectories(runtimeMapFolder);
        byte[] mapYamlBytes = null;
        List<GameMap> submaps = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = fileRegistry.normalizeFileName(entry.getName());
                byte[] entryBytes = zipInputStream.readAllBytes();
                if (assetName != null && entryName.equals(assetName)) {
                    if (runtimeAssetPath == null) {
                        throw new IllegalStateException("Map " + sourceName + " has an asset name but no runtime asset path.");
                    }

                    Files.createDirectories(runtimeAssetPath.getParent());
                    Files.write(runtimeAssetPath, entryBytes);
                    continue;
                }
                if (entryName.equals("map.yml")) {
                    mapYamlBytes = entryBytes;
                    continue;
                }
                if (entryName.startsWith("submaps/") && entryName.endsWith(".dmap")) {
                    String submapName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    Path submapRuntimeFolder = runtimeMapFolder
                        .resolve("submaps")
                        .resolve(stripExtension(submapName));
                    submaps.add(readGameMap(
                        new ByteArrayInputStream(entryBytes),
                        submapName,
                        submapRuntimeFolder,
                        null,
                        null
                    ));
                    continue;
                }
            }
        }
        if (mapYamlBytes == null) {
            throw new IllegalStateException("Map " + sourceName + " is missing map.yml!");
        }
        YamlConfiguration metadata = fileRegistry.loadYaml(mapYamlBytes);
        String id = metadata.getString("id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Map " + sourceName + " is missing an id!");
        }
        return new GameMap(
            id,
            metadata.getString("name", id),
            metadata.getStringList("tags"),
            readPoints(metadata.getConfigurationSection("points")),
            readRegions(metadata.getConfigurationSection("regions")),
            List.copyOf(submaps)
        );
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
    private Map<String, List<GameMap.MapRegion>> readRegions(org.bukkit.configuration.ConfigurationSection section) {
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
                Map<?, ?> minMap = asMap(regionMap.get("min"), regionName, "min");
                Map<?, ?> maxMap = asMap(regionMap.get("max"), regionName, "max");
                entries.add(new GameMap.MapRegion(
                    new GameMap.MapPoint(
                        readRequiredInt(minMap, "x", regionName),
                        readRequiredInt(minMap, "y", regionName),
                        readRequiredInt(minMap, "z", regionName)
                    ),
                    new GameMap.MapPoint(
                        readRequiredInt(maxMap, "x", regionName),
                        readRequiredInt(maxMap, "y", regionName),
                        readRequiredInt(maxMap, "z", regionName)
                    )
                ));
            }
            regions.put(regionName, List.copyOf(entries));
        }
        return Map.copyOf(regions);
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
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
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Entry " + entryName + " has a non-integer " + key + ".", e);
        }
    }
}
