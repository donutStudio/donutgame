package com.donutsforlife11.donutgame.internal.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.GameWorldDescriptor;

public class FileService {
    private final Map<String, GameModuleDescriptor> gameModules = new HashMap<>();
    private final Map<String, GameMapDescriptor> gameMaps = new HashMap<>();
    private final List<URLClassLoader> moduleLoaders = new ArrayList<>();
    private final Plugin plugin;
    private final File modulesFolder;
    private final File mapsFolder;
    private final File runtimeFolder;

    public FileService(Plugin plugin) {
        this.plugin = plugin;
        this.modulesFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("modules_directory", "modules"));
        this.mapsFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("maps_directory", "maps"));
        this.runtimeFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("runtime_directory", ".runtime"));
    }

    public void reload() {
        gameModules.clear();
        gameMaps.clear();
        closeModuleLoaders();
        moduleLoaders.clear();
        loadModuleFiles();
        loadMapFiles();
    }

    private void loadModuleFiles() {
        for (File module : getFolderContentsRecursive(modulesFolder, ".jar")) {
            try {
                GameModuleDescriptor descriptor = extractGameData(module);
                if (gameModules.containsKey(descriptor.id())) {
                    throw new IllegalStateException("Duplicate module id '" + descriptor.id() + "' from " + module.getName() + ".");
                }
                gameModules.put(descriptor.id(), descriptor);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load module descriptor from " + module.getName() + ": " + e.getMessage());
            }
        }
    }

    private void loadMapFiles() {
        for (File map : getFolderContentsRecursive(mapsFolder, ".dmap")) {
            try {
                GameMapDescriptor descriptor = extractMapData(map);
                if (gameMaps.put(descriptor.id(), descriptor) != null) {
                    plugin.getLogger().warning("Duplicate map id '" + descriptor.id() + "' from " + map.getName() + ".");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load map descriptor from " + map.getName() + ": " + e.getMessage());
            }
        }
    }

    public Map<String, GameModuleDescriptor> gameModules() {
        return Map.copyOf(gameModules);
    }

    public Map<String, GameMapDescriptor> gameMaps() {
        return Map.copyOf(gameMaps);
    }

    public File modulesFolder() {
        return modulesFolder;
    }

    public File mapsFolder() {
        return mapsFolder;
    }

    public File runtimeFolder() {
        return runtimeFolder;
    }

    public void closeModuleLoaders() {
        for (URLClassLoader loader : moduleLoaders) {
            try {
                loader.close();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to close module classloader: " + e.getMessage());
            }
        }
    }

    private GameModuleDescriptor extractGameData(File gameModule) throws Exception {
        URL[] urls = { gameModule.toURI().toURL() };

        URLClassLoader loader = new URLClassLoader(urls, Donutgame.class.getClassLoader());
        moduleLoaders.add(loader);

        String configText;
        try (JarFile jar = new JarFile(gameModule)) {
            JarEntry configEntry = jar.getJarEntry("config.yml");
            if (configEntry == null) {
                throw new IllegalArgumentException("Module jar is missing config.yml: " + gameModule.getName());
            }
            try (InputStream stream = jar.getInputStream(configEntry)) {
                configText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(configText));

        String gameId = config.getString("id");
        String mainClass = config.getString("main_class");
        String name = config.getString("name", gameId);

        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Module config is missing id");
        }

        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("Module config is missing main_class");
        }

        Class<? extends GameModule> moduleClass = loader.loadClass(mainClass).asSubclass(GameModule.class);
        return new GameModuleDescriptor(gameId, mainClass, name, moduleClass, configText, gameModule);
    }

    private GameMapDescriptor extractMapData(File gameMap) throws IOException {
        Path normalizedPath = gameMap.toPath().toAbsolutePath().normalize();
        Path runtimeMapFolder = runtimeFolder.toPath()
            .resolve("maps")
            .resolve(stripExtension(sanitizeFileName(gameMap.getName())))
            .toAbsolutePath()
            .normalize();

        try (InputStream inputStream = Files.newInputStream(normalizedPath)) {
            return readMapDescriptor(inputStream, normalizedPath, runtimeMapFolder, gameMap.getName());
        }
    }

    private GameMapDescriptor readMapDescriptor(
        InputStream inputStream,
        Path mapPath,
        Path runtimeMapFolder,
        String sourceName
    ) throws IOException {
        byte[] metadataBytes = null;
        String assetName = null;
        BackingType backingType = BackingType.NONE;
        Map<String, GameMapDescriptor> submaps = new LinkedHashMap<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeFileName(entry.getName());
                if (entryName.equals("map.yml")) {
                    metadataBytes = zipInputStream.readAllBytes();
                    continue;
                }
                if (entryName.startsWith("submaps/") && entryName.endsWith(".dmap")) {
                    byte[] submapBytes = zipInputStream.readAllBytes();
                    String submapFileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    Path submapRuntimeFolder = runtimeMapFolder
                        .resolve("submaps")
                        .resolve(stripExtension(sanitizeFileName(submapFileName)))
                        .normalize();
                    GameMapDescriptor submap = readMapDescriptor(
                        new ByteArrayInputStream(submapBytes),
                        mapPath,
                        submapRuntimeFolder,
                        sourceName + ":" + submapFileName
                    );
                    submaps.put(submap.id(), submap);
                    continue;
                }
                if (!entryName.contains("/") && isMapAsset(entryName)) {
                    if (backingType != BackingType.NONE) {
                        throw new IllegalStateException("Map " + sourceName + " contains multiple backing assets.");
                    }
                    assetName = entryName;
                    backingType = backingType(entryName);
                }
            }
        }

        if (metadataBytes == null) {
            throw new IllegalStateException("Map " + sourceName + " is missing map.yml.");
        }

        YamlConfiguration config = loadYaml(metadataBytes);
        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Map " + sourceName + " is missing an id.");
        }

        GameWorldDescriptor backingWorld = null;
        if (backingType != BackingType.NONE) {
            backingWorld = new GameWorldDescriptor(
                GameMapDescriptor.DEFAULT_WORLD_ID,
                backingType,
                assetName,
                runtimeMapFolder.resolve(assetName).normalize()
            );
        }

        return new GameMapDescriptor(
            id,
            config.getString("name", id),
            config.getStringList("tags"),
            mapPath,
            runtimeMapFolder,
            "map.yml",
            new String(metadataBytes, StandardCharsets.UTF_8),
            backingWorld,
            submaps
        );
    }

    private boolean isMapAsset(String entryName) {
        return entryName.toLowerCase(Locale.ROOT).endsWith(".slime")
            || entryName.toLowerCase(Locale.ROOT).endsWith(".schem");
    }

    private BackingType backingType(String entryName) {
        String lowerName = entryName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".slime")) return BackingType.WORLD;
        if (lowerName.endsWith(".schem")) return BackingType.SCHEMATIC;
        return BackingType.NONE;
    }

    private List<File> getFolderContentsRecursive(File folder, String extension) {
        if (!folder.exists()) folder.mkdirs();
        File[] fileList = folder.listFiles();
        if (fileList == null) return List.of();
        List<File> files = new ArrayList<>();
        for (File file : fileList) {
            if (file.isDirectory()) files.addAll(getFolderContentsRecursive(file, extension));
            else if (extension == null || file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) files.add(file);
        }
        files.sort(Comparator.comparing(file -> file.getAbsolutePath()));
        return files;
    }

    public YamlConfiguration loadYaml(byte[] yamlBytes) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(yamlBytes), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    public String normalizeFileName(String fileName) {
        return fileName.replace('\\', '/');
    }

    public GameMapDescriptor getGameMapDescriptor(String id) {
        GameMapDescriptor descriptor = gameMaps.get(id);
        if (descriptor == null) throw new IllegalArgumentException("Unknown map id: " + id);
        return descriptor;
    }

    private String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
