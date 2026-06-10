package com.donutsforlife11.donutgame.api.map;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MapRepository {
    private final Path mapsDirectory;
    private final Path cacheDirectory;
    private final Map<Path, CachedMap> cache = new ConcurrentHashMap<>();

    public MapRepository(Path mapsDirectory, Path cacheDirectory) {
        this.mapsDirectory = mapsDirectory;
        this.cacheDirectory = cacheDirectory;

        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create map runtime cache directory " + cacheDirectory, e);
        }
    }

    public GameMap load(String mapPath) {
        if (mapPath == null || mapPath.isBlank()) {
            throw new IllegalArgumentException("Map path cannot be blank.");
        }

        return load(mapsDirectory.resolve(mapPath).normalize());
    }

    public GameMap load(Path mapFile) {
        try {
            Path normalizedPath = mapFile.toAbsolutePath().normalize();

            if (!Files.isRegularFile(normalizedPath)) {
                throw new IllegalArgumentException("Map file does not exist: " + normalizedPath);
            }

            long lastModified = Files.getLastModifiedTime(normalizedPath).toMillis();
            CachedMap cachedMap = cache.get(normalizedPath);

            if (cachedMap != null && cachedMap.lastModified() == lastModified) {
                return cachedMap.map();
            }

            Path mapCacheDirectory = cacheDirectory.resolve(sanitizeFileName(normalizedPath.getFileName().toString()) + "_" + lastModified);
            deleteSiblingCacheDirectories(normalizedPath.getFileName().toString(), mapCacheDirectory);
            deleteRecursively(mapCacheDirectory);
            Files.createDirectories(mapCacheDirectory);

            GameMap map;
            try (InputStream inputStream = Files.newInputStream(normalizedPath)) {
                map = readMap(inputStream, normalizedPath.getFileName().toString(), mapCacheDirectory);
            }

            cache.put(normalizedPath, new CachedMap(lastModified, map));
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read map file " + mapFile, e);
        }
    }

    public void shutdown() {
        cache.clear();

        try {
            deleteRecursively(cacheDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clear map runtime cache " + cacheDirectory, e);
        }
    }

    private GameMap readMap(InputStream stream, String sourceName, Path cacheRoot) throws IOException {
        byte[] mapYamlBytes = null;
        String backingFileName = null;
        byte[] backingFileBytes = null;
        GameMap.BackingType backingType = GameMap.BackingType.NONE;
        List<GameMap> submaps = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(stream)) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = normalizeEntryName(entry.getName());
                byte[] entryBytes = zipInputStream.readAllBytes();

                if ("map.yml".equals(entryName)) {
                    mapYamlBytes = entryBytes;
                    continue;
                }

                if (entryName.startsWith("submaps/") && entryName.endsWith(".dmap")) {
                    String submapName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    Path submapCacheDirectory = cacheRoot.resolve("submaps").resolve(stripExtension(submapName));
                    Files.createDirectories(submapCacheDirectory);
                    submaps.add(readMap(new ByteArrayInputStream(entryBytes), submapName, submapCacheDirectory));
                    continue;
                }

                if (!entryName.contains("/")) {
                    if (entryName.endsWith(".schem")) {
                        if (backingType != GameMap.BackingType.NONE) {
                            throw new IllegalStateException("Map " + sourceName + " contains multiple backing assets.");
                        }

                        backingType = GameMap.BackingType.SCHEMATIC;
                        backingFileName = entryName;
                        backingFileBytes = entryBytes;
                        continue;
                    }

                    if (entryName.endsWith(".slime")) {
                        if (backingType != GameMap.BackingType.NONE) {
                            throw new IllegalStateException("Map " + sourceName + " contains multiple backing assets.");
                        }

                        backingType = GameMap.BackingType.SLIME;
                        backingFileName = entryName;
                        backingFileBytes = entryBytes;
                    }
                }
            }
        }

        if (mapYamlBytes == null) {
            throw new IllegalStateException("Map " + sourceName + " is missing map.yml.");
        }

        YamlConfiguration yaml = loadYaml(mapYamlBytes);
        String id = yaml.getString("id");

        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Map " + sourceName + " is missing a non-blank id.");
        }

        Path schematicPath = null;
        Path slimePath = null;
        if (backingFileName != null && backingFileBytes != null) {
            Path assetPath = cacheRoot.resolve(backingFileName);
            Files.createDirectories(assetPath.getParent());
            Files.copy(new ByteArrayInputStream(backingFileBytes), assetPath, StandardCopyOption.REPLACE_EXISTING);

            if (backingType == GameMap.BackingType.SCHEMATIC) {
                schematicPath = assetPath;
            } else if (backingType == GameMap.BackingType.SLIME) {
                slimePath = assetPath;
            }
        }

        return new GameMap(
            id,
            yaml.getString("name", id),
            yaml.getStringList("tags"),
            readPoints(yaml.getConfigurationSection("points")),
            readRegions(yaml.getConfigurationSection("regions")),
            submaps,
            backingType,
            schematicPath,
            slimePath,
            sourceName,
            null
        );
    }

    private Map<String, List<GameMap.IntPoint>> readPoints(ConfigurationSection section) {
        Map<String, List<GameMap.IntPoint>> points = new LinkedHashMap<>();

        if (section == null) {
            return points;
        }

        for (String pointName : section.getKeys(false)) {
            List<?> rawPoints = section.getList(pointName);
            if (rawPoints == null) {
                continue;
            }

            List<GameMap.IntPoint> entries = new ArrayList<>();
            for (Object rawPoint : rawPoints) {
                if (!(rawPoint instanceof Map<?, ?> pointMap)) {
                    continue;
                }

                entries.add(new GameMap.IntPoint(
                    readRequiredInt(pointMap, "x", pointName),
                    readRequiredInt(pointMap, "y", pointName),
                    readRequiredInt(pointMap, "z", pointName)
                ));
            }

            points.put(pointName, List.copyOf(entries));
        }

        return points;
    }

    private Map<String, List<GameMap.IntRegion>> readRegions(ConfigurationSection section) {
        Map<String, List<GameMap.IntRegion>> regions = new LinkedHashMap<>();

        if (section == null) {
            return regions;
        }

        for (String regionName : section.getKeys(false)) {
            List<?> rawRegions = section.getList(regionName);
            if (rawRegions == null) {
                continue;
            }

            List<GameMap.IntRegion> entries = new ArrayList<>();
            for (Object rawRegion : rawRegions) {
                if (!(rawRegion instanceof Map<?, ?> regionMap)) {
                    continue;
                }

                Map<?, ?> minMap = asMap(regionMap.get("min"), regionName, "min");
                Map<?, ?> maxMap = asMap(regionMap.get("max"), regionName, "max");

                entries.add(new GameMap.IntRegion(
                    new GameMap.IntPoint(
                        readRequiredInt(minMap, "x", regionName),
                        readRequiredInt(minMap, "y", regionName),
                        readRequiredInt(minMap, "z", regionName)
                    ),
                    new GameMap.IntPoint(
                        readRequiredInt(maxMap, "x", regionName),
                        readRequiredInt(maxMap, "y", regionName),
                        readRequiredInt(maxMap, "z", regionName)
                    )
                ));
            }

            regions.put(regionName, List.copyOf(entries));
        }

        return regions;
    }

    private Map<?, ?> asMap(Object value, String regionName, String keyName) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }

        throw new IllegalStateException("Region " + regionName + " is missing a " + keyName + " coordinate object.");
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
            return Integer.parseInt(Objects.toString(value));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Entry " + entryName + " has a non-integer " + key + ".", e);
        }
    }

    private YamlConfiguration loadYaml(byte[] yamlBytes) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(yamlBytes), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private String normalizeEntryName(String entryName) {
        return entryName.replace('\\', '/');
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void deleteSiblingCacheDirectories(String fileName, Path keepPath) throws IOException {
        if (!Files.isDirectory(cacheDirectory)) {
            return;
        }

        String prefix = sanitizeFileName(fileName) + "_";
        try (var children = Files.list(cacheDirectory)) {
            for (Path child : children.toList()) {
                if (!child.equals(keepPath) && child.getFileName().toString().startsWith(prefix)) {
                    deleteRecursively(child);
                }
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            List<Path> paths = walk.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            for (Path currentPath : paths) {
                Files.deleteIfExists(currentPath);
            }
        }
    }

    private record CachedMap(long lastModified, GameMap map) {
    }
}
