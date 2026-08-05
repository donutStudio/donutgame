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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor.BackingType;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.map.GameMapDescriptor;

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
        this.modulesFolder = new File(this.plugin.getDataFolder(), plugin.getConfig().getString("modules_directory"));
        this.mapsFolder = new File(this.plugin.getDataFolder(), plugin.getConfig().getString("maps_directory"));
        this.runtimeFolder = new File(this.plugin.getDataFolder(), plugin.getConfig().getString("runtime_directory"));
    }

    public void reload() {
        gameModules.clear();
        gameMaps.clear();
        loadModuleFiles();
        loadMapFiles();
    }
    private void loadModuleFiles() {
        for (File module : getFolderContentsRecursive(modulesFolder, ".jar")) {
            try {
                GameModuleDescriptor descriptor = extractGameData(module);
                gameModules.put(descriptor.id(), descriptor);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void loadMapFiles() {
        for (File map : getFolderContentsRecursive(mapsFolder, ".dmap")) {
            try {
                GameMapDescriptor descriptor = extractMapData(map);
                gameMaps.put(descriptor.id(), descriptor);
            } catch(Exception e) {
                e.printStackTrace();
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
        try {
            for (URLClassLoader loader : moduleLoaders) {
                loader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                throw new IllegalArgumentException(
                    "Module jar is missing config.yml: " + gameModule.getName()
                );
            }
            try (InputStream stream = jar.getInputStream(configEntry)) {
                configText = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
                );
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
        return new GameModuleDescriptor(gameId, mainClass, name, moduleClass, configText);
    }
    private GameMapDescriptor extractMapData(File gameMap) throws Exception {
        try {
            Path normalizedPath = gameMap.toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedPath)) {
                throw new IllegalArgumentException("Map file does not exist: " + normalizedPath);
            }
            try (
                InputStream inputStream = Files.newInputStream(normalizedPath);
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)
            ) {
                ZipEntry entry;

                String id = null;
                String name = null;
                BackingType backingType = BackingType.NONE;
                String backingEntry = null;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = normalizeFileName(entry.getName());
                    if (entryName.equals("map.yml")) {
                        YamlConfiguration metadata = loadYaml(zipInputStream.readAllBytes());
                        id = metadata.getString("id");
                        if (id == null || id.isBlank()) {
                            throw new IllegalStateException("Map " + normalizedPath + " is missing an id!");
                        }
                        name = metadata.getString("name", id);
                        continue;
                    }
                    if (!entryName.contains("/")) {
                        if (entryName.endsWith(".slime")) {
                            if (backingType != BackingType.NONE) {
                                throw new IllegalStateException("Map contains multiple backing assets");
                            }
                            backingType = BackingType.WORLD;
                            backingEntry = entryName;
                        }

                        if (entryName.endsWith(".schem")) {
                            if (backingType != BackingType.NONE) {
                                throw new IllegalStateException("Map contains multiple backing assets");
                            }
                            backingType = BackingType.SCHEMATIC;
                            backingEntry = entryName;
                        }
                    }
                }
                if (id == null) {
                    throw new IllegalStateException("Map " + normalizedPath + " is missing map.yml!");
                }
                Path runtimeMapFolder = runtimeFolder.toPath()
                .resolve("maps")
                .resolve(sanitizeFileName(id))
                .toAbsolutePath()
                .normalize();

            Path runtimeAssetPath = backingEntry == null
                ? null
                : runtimeMapFolder.resolve(backingEntry).toAbsolutePath().normalize();

            return new GameMapDescriptor(
                id,
                name,
                normalizedPath,
                runtimeMapFolder,
                backingType,
                backingEntry,
                runtimeAssetPath
            );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read map descriptor from " + gameMap.toPath(), e);
        }
    }

    private List<File> getFolderContentsRecursive(File folder, String extension) {
        List<File> files = new ArrayList<>();

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] fileList = folder.listFiles();
        if (fileList == null) {
            return files;
        }

        for (File file : fileList) {
            if (file.isDirectory()) {
                files.addAll(getFolderContentsRecursive(file, extension));
            } else if (extension == null || (extension != null && file.getName().toLowerCase().endsWith(extension))) {
                files.add(file);
            }
        }
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
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown map id: " + id);
        }
        return descriptor;
    }
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
