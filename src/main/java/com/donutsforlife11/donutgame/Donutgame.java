package com.donutsforlife11.donutgame;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.PlayerStateStore;

public final class Donutgame extends JavaPlugin {
    private final FileService fileService = new FileService(this);
    private final ModuleService moduleService = new ModuleService(this);
    private final MapService mapService = new MapService(fileService);
    private final PlayerStateStore playerStateStore = new PlayerStateStore();
    private final WorldService worldService = new WorldService(this, playerStateStore);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fileService.reload();

        new CommandRegistrar(this, List.of(
            new MinigameCommand(fileService, moduleService),
            new WorldTeleportCommand()
        )).register();
    }

    @Override
    public void onDisable() {

    }

    public FileService fileService() {
        return fileService;
    }
    public ModuleService moduleService() {
        return moduleService;
    }
    public MapService mapService() {
        return mapService;
    }
    public WorldService worldService() {
        return worldService;
    }
}
