package com.donutsforlife11.donutgame;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.api.map.MapRepository;
import com.donutsforlife11.donutgame.api.map.WorldManager;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileManager;
import com.donutsforlife11.donutgame.internal.module.GameModule;
import com.donutsforlife11.donutgame.internal.module.GameModuleDescriptor;
import com.donutsforlife11.donutgame.internal.module.ModuleManager;
import com.donutsforlife11.donutgame.internal.player.GamePlayerEvents;
import com.donutsforlife11.donutgame.internal.player.PlayerEvents;
import com.donutsforlife11.donutgame.internal.player.PlayerStateStore;

public final class Donutgame extends JavaPlugin {
    protected File modulesFolder;
    protected File mapsFolder;
    protected File runtimeFolder;
    private PlayerStateStore playerStateStore = new PlayerStateStore();

    private WorldManager worldManager;
    private MapRepository mapRepository;
    private PlayerEvents playerEvents;
    private ModuleManager moduleManager;

    private final List<URLClassLoader> moduleLoaders = new ArrayList<>();
    private final Map<String, GameModuleDescriptor> gameModules = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        modulesFolder = new File(getDataFolder(), getConfig().getString("modules_directory"));
        mapsFolder = new File(getDataFolder(), getConfig().getString("maps_directory"));
        runtimeFolder = new File(getDataFolder(), getConfig().getString("runtime_directory"));
        modulesFolder.mkdirs();
        mapsFolder.mkdirs();
        runtimeFolder.mkdirs();

        worldManager = new WorldManager(this);
        mapRepository = new MapRepository(mapsFolder.toPath(), runtimeFolder.toPath().resolve("maps"));
        playerEvents = new PlayerEvents(playerStateStore, worldManager);
        moduleManager = new ModuleManager(this);

        for (File module : FileManager.getFolderContentsRecursive(modulesFolder, ".jar")) {
            try {
                GameModuleDescriptor descriptor = extractGameData(module);
                gameModules.put(descriptor.id(), descriptor);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        getServer().getPluginManager().registerEvents(playerEvents, this);
        getServer().getPluginManager().registerEvents(new GamePlayerEvents(moduleManager), this);

        new CommandRegistrar(this, List.of(
            new WorldTeleportCommand()
        )).register();
    }

    @Override
    public void onDisable() {

    }

    public GameModuleDescriptor extractGameData(File gameModule) throws Exception {
        URL[] urls = { gameModule.toURI().toURL() };

        URLClassLoader loader = new URLClassLoader(urls, Donutgame.class.getClassLoader());
        moduleLoaders.add(loader);

        String configText;
        try (InputStream stream = loader.getResourceAsStream("config.yml")) {
            if (stream == null) {
                throw new IllegalArgumentException("Module jar is missing config.yml: " + gameModule.getName());
            }

            configText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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

    public Map<String, GameModuleDescriptor> gameModules() {
        return gameModules;
    }
}
