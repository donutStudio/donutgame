package com.donutsforlife11.donutgame;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GamePlayerEvents;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.PlayerEvents;
import com.donutsforlife11.donutgame.internal.player.PlayerStateStore;

public final class Donutgame extends JavaPlugin {
    private final FileService fileService = new FileService(this);
    private final ModuleService moduleService = new ModuleService(this);
    private final MapService mapService = new MapService(fileService);
    private final PlayerStateStore playerStateStore = new PlayerStateStore();
    private final WorldService worldService = new WorldService(this, playerStateStore);

    private PlayerEvents playerEvents = new PlayerEvents(playerStateStore, worldService);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fileService.reload();

        getServer().getPluginManager().registerEvents(playerEvents, this);
        getServer().getPluginManager().registerEvents(new GamePlayerEvents(moduleService), this);

        new CommandRegistrar(this, List.of(
            new MinigameCommand(fileService, moduleService),
            new WorldTeleportCommand()
        )).register();
    }

    @Override
    public void onDisable() {
        moduleService.unloadAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerStateStore.save(player.getUniqueId(), player.getWorld().getName(), playerEvents.savePlayerState(player));
        }
        fileService.closeModuleLoaders();
        worldService.unloadAll();
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
