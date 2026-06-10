package com.donutsforlife11.donutgame;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.api.map.MapRepository;
import com.donutsforlife11.donutgame.game.GameModuleDescriptor;
import com.donutsforlife11.donutgame.game.GameModule;
import com.donutsforlife11.donutgame.game.ModuleManager;
import com.donutsforlife11.donutgame.player.GamePlayerEvents;
import com.donutsforlife11.donutgame.player.PlayerStateStore;
import com.donutsforlife11.donutgame.util.CommandRegistrar;
import com.donutsforlife11.donutgame.util.FileManager;
import com.donutsforlife11.donutgame.util.PlayerEvents;
import com.donutsforlife11.donutgame.util.WorldManager;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;

public final class Donutgame extends JavaPlugin {
    
    protected final File modulesFolder = new File(getDataFolder(), "modules");
    protected final File mapsFolder = new File(getDataFolder(), "maps");
    protected final File runtimeFolder = new File(getDataFolder(), ".runtime");
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private PlayerStateStore playerStateStore = new PlayerStateStore();
    private PlayerEvents playerEvents;
    private final List<URLClassLoader> moduleLoaders = new ArrayList<>();
    private Map<String, GameModuleDescriptor> gameModules = new HashMap<>();

    private ModuleManager moduleManager;
    private WorldManager worldManager;
    private MapRepository mapRepository;

    @Override
    public void onEnable() {
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
            new WorldTeleportCommand(worldManager),
            new MinigameCommand(this, moduleManager)
        )).register();
    }

    @Override
    public void onDisable() {
        try {
            for (URLClassLoader loader : moduleLoaders) {
                loader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String worldStateId = worldManager.getPlayerStateId(player.getWorld());
            playerStateStore.save(player.getUniqueId(), worldStateId, playerEvents.savePlayerState(player));
        }

        for (int gameIndex : List.copyOf(moduleManager.getActiveGames().keySet())) {
            moduleManager.unloadModule(gameIndex).join();
        }

        for (SlimeWorldInstance instance : asp.getLoadedWorlds()) {
            try {
                asp.saveWorld(instance);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bukkit.unloadWorld(instance.getBukkitWorld(), false);
        }

        mapRepository.shutdown();
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

    public Map<String, GameModuleDescriptor> getGameModules() {
        return gameModules;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public PlayerStateStore getPlayerStateStore() {
        return playerStateStore;
    }

    public File getMapsFolder() {
        return mapsFolder;
    }

    public MapRepository getMapRepository() {
        return mapRepository;
    }
}
