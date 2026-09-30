package com.donutsforlife11.donutgame;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import com.donutsforlife11.donutgame.commands.DonutgameCommand;
import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GameItemComponentEvents;
import com.donutsforlife11.donutgame.internal.game.GameKillCreditEvents;
import com.donutsforlife11.donutgame.internal.game.GamePlayerConnectionEvents;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.game.SpectatorGuardEvents;
import com.donutsforlife11.donutgame.internal.game.SpectatorMenuEvents;
import com.donutsforlife11.donutgame.internal.game.SpectatorService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.PlayerWorldStateService;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

public final class Donutgame extends JavaPlugin {
    private FileService fileService;
    private MapService mapService;
    private WorldService worldService;
    private ModuleService moduleService;
    private PlayerWorldStateService playerWorldStateService;
    private GlowService glowService;
    private SpectatorService spectatorService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        fileService = new FileService(this);
        fileService.reload();
        mapService = new MapService(fileService);
        playerWorldStateService = new PlayerWorldStateService(this);
        glowService = new GlowService(this);
        glowService.enable();
        spectatorService = new SpectatorService(this);
        spectatorService.enable();
        worldService = new WorldService(this, playerWorldStateService);
        moduleService = new ModuleService(this);
        getServer().getPluginManager().registerEvents(new GamePlayerConnectionEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new GameKillCreditEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new GameItemComponentEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new SpectatorMenuEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new SpectatorGuardEvents(moduleService), this);
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
        if (playerWorldStateService != null) {
            playerWorldStateService.cleanupRuntimeState();
        }
        if (glowService != null) {
            glowService.disable();
        }
        if (spectatorService != null) {
            spectatorService.disable();
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

    public GlowService glowService() {
        return glowService;
    }

    public SpectatorService spectatorService() {
        return spectatorService;
    }
}
