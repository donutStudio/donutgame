package com.donutsforlife11.donutgame;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.api.ModuleApi;
import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;
import com.donutsforlife11.donutgame.game.ModuleManager;
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
    protected final File worldsFolder = new File(getDataFolder(), "worlds");
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private PlayerStateStore playerStateStore = new PlayerStateStore();
    private PlayerEvents playerEvents;
    private Map<String, Class<? extends GameModule>> gameModuleClasses = new HashMap<>();

    private GameContext context;
    private ModuleManager moduleManager;
    private WorldManager worldManager;

    URLClassLoader loader;

    @Override
    public void onEnable() {
        playerEvents = new PlayerEvents(playerStateStore);
        worldManager = new WorldManager(this, worldsFolder);

        context = new ModuleApi(this);
        moduleManager = new ModuleManager(context);

        for (File module : FileManager.getFolderContentsRecursive(modulesFolder, ".jar")) {
            try {
                Map.Entry<String, Class<? extends GameModule>> entry = extractGameData(module);
                gameModuleClasses.put(entry.getKey(), entry.getValue());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        getServer().getPluginManager().registerEvents(playerEvents, this);

        new CommandRegistrar(this, List.of(
            new WorldTeleportCommand(worldManager),
            new MinigameCommand(this, moduleManager)
        )).register();
    }

    @Override
    public void onDisable() {
        getLogger();
        try {
            loader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerStateStore.save(player.getUniqueId(), player.getWorld().getName(), playerEvents.savePlayerState(player));
        }

        for (SlimeWorldInstance instance : asp.getLoadedWorlds()) {
            try {
                asp.saveWorld(instance);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bukkit.unloadWorld(instance.getBukkitWorld(), false);
        }

        for (int gameIndex : moduleManager.getActiveGames().keySet()) {
            moduleManager.unloadModule(gameIndex);
        }
    }

    public Map.Entry<String, Class<? extends GameModule>> extractGameData(File gameModule) throws Exception {
        URL[] urls = { gameModule.toURI().toURL() };

        loader = new URLClassLoader(urls, Donutgame.class.getClassLoader());

        InputStream stream = loader.getResourceAsStream("config.yml");
        if (stream == null) {
            throw new IllegalArgumentException("Module jar is missing config.yml: " + gameModule.getName());
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        String gameId = config.getString("id");
        String mainClass = config.getString("main_class");

        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Module config is missing id");
        }

        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("Module config is missing main_class");
        }

        Class<? extends GameModule> moduleClass = loader.loadClass(mainClass).asSubclass(GameModule.class);
        return Map.entry(gameId, moduleClass);
    }

    public Map<String, Class<? extends GameModule>> getGameModuleClasses() {
        return gameModuleClasses;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public File getMapsFolder() {
        return mapsFolder;
    }
}
