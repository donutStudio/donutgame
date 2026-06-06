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
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
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

    private ModuleManager moduleManager;
    private WorldManager worldManager;

    URLClassLoader loader;

    @Override
    public void onEnable() {
        worldManager = new WorldManager(this, worldsFolder);
        playerEvents = new PlayerEvents(playerStateStore, worldManager);

        moduleManager = new ModuleManager(gameId -> {
            TeamManager teamManager = new TeamManager();
            TimeManager timeManager = new TimeManager(this);
            return new ModuleApi(this, teamManager, timeManager);
        });

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
            if (loader != null) {
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
            moduleManager.unloadModule(gameIndex);
        }

        for (SlimeWorldInstance instance : asp.getLoadedWorlds()) {
            try {
                asp.saveWorld(instance);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bukkit.unloadWorld(instance.getBukkitWorld(), false);
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

    public PlayerStateStore getPlayerStateStore() {
        return playerStateStore;
    }

    public File getMapsFolder() {
        return mapsFolder;
    }
}
