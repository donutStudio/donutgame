package com.donutsforlife11.donutgame;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.commands.DonutgameCommand;
import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GamePlayerEvents;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.item.GameItemEvents;
import com.donutsforlife11.donutgame.internal.item.GameItemService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.ui.GlowService;
import com.donutsforlife11.donutgame.internal.ui.SpectatorTabListService;

public final class Donutgame extends JavaPlugin {
    private FileService fileService;
    private MapService mapService;
    private WorldService worldService;
    private GlowService glowService;
    private GameItemService itemService;
    private SpectatorTabListService spectatorTabListService;
    private final ModuleService moduleService = new ModuleService(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fileService = new FileService(this);
        mapService = new MapService(fileService);
        worldService = new WorldService(this);
        glowService = new GlowService(this);
        itemService = new GameItemService(moduleService);
        glowService.enable();
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            spectatorTabListService = new SpectatorTabListService(this, moduleService);
            spectatorTabListService.enable();
        } else {
            getLogger().info("ProtocolLib is not installed; spectators will stay listed but will not get packet-level translucent tab-list styling.");
        }
        fileService.reload();

        getServer().getPluginManager().registerEvents(new GamePlayerEvents(moduleService), this);
        getServer().getPluginManager().registerEvents(new GameItemEvents(moduleService, itemService), this);

        new CommandRegistrar(this, List.of(
            new DonutgameCommand(this),
            new MinigameCommand(fileService, moduleService),
            new WorldTeleportCommand()
        )).register();
    }

    @Override
    public void onDisable() {
        moduleService.unloadAll();
        fileService.closeModuleLoaders();
        worldService.unloadAll();
        if (spectatorTabListService != null) spectatorTabListService.disable();
        if (glowService != null) glowService.disable();
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
    public GlowService glowService() {
        return glowService;
    }
    public GameItemService itemService() {
        return itemService;
    }
}
