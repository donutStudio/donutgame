package com.donutsforlife11.donutgame;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import com.donutsforlife11.donutgame.commands.DonutgameCommand;
import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GameKillCreditEvents;
import com.donutsforlife11.donutgame.internal.game.GamePlayerConnectionEvents;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.PlayerWorldStateService;

public final class Donutgame extends JavaPlugin {
    private FileService fileService;
    private MapService mapService;
    private WorldService worldService;
    private ModuleService moduleService;
    private PlayerWorldStateService playerWorldStateService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        fileService = new FileService(this);
        fileService.reload();
        mapService = new MapService(fileService);
        playerWorldStateService = new PlayerWorldStateService();
        worldService = new WorldService(this, playerWorldStateService);
        moduleService = new ModuleService(this);
        getServer().getPluginManager().registerEvents(new GamePlayerConnectionEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new GameKillCreditEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(playerWorldStateService, this);

        new CommandRegistrar(this, List.of(
            new DonutgameCommand(this),
            new MinigameCommand(fileService, moduleService),
            new WorldTeleportCommand()
        )).register();

        getLogger().info(
            "Donutgame enabled with "
                + fileService.gameModules().size()
                + " discovered module(s) and "
                + fileService.gameMaps().size()
                + " discovered map(s)."
        );
    }

    @Override
    public void onDisable() {
        if (moduleService != null) {
            moduleService.unloadAll();
        }
        if (worldService != null) {
            worldService.unloadAll();
        }
        if (fileService != null) {
            fileService.closeModuleLoaders();
        }
    }

    public FileService fileService() {
        return fileService;
    }

    public MapService mapService() {
        return mapService;
    }

    public WorldService worldService() {
        return worldService;
    }

    public ModuleService moduleService() {
        return moduleService;
    }

    public PlayerWorldStateService playerWorldStateService() {
        return playerWorldStateService;
    }
}
