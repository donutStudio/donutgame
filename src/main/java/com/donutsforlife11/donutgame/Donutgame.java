package com.donutsforlife11.donutgame;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.commands.MinigameCommand;
import com.donutsforlife11.donutgame.commands.DonutgameCommand;
import com.donutsforlife11.donutgame.commands.WorldTeleportCommand;
import com.donutsforlife11.donutgame.internal.command.CommandRegistrar;
import com.donutsforlife11.donutgame.internal.file.FileService;
import com.donutsforlife11.donutgame.internal.game.GamePlayerEvents;
import com.donutsforlife11.donutgame.internal.game.ModuleService;
import com.donutsforlife11.donutgame.internal.item.GameItemEvents;
import com.donutsforlife11.donutgame.internal.item.GameItemService;
import com.donutsforlife11.donutgame.internal.map.MapService;
import com.donutsforlife11.donutgame.internal.map.WorldService;
import com.donutsforlife11.donutgame.internal.player.PlayerEvents;
import com.donutsforlife11.donutgame.internal.player.PlayerStateStore;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

public final class Donutgame extends JavaPlugin {
    private FileService fileService;
    private MapService mapService;
    private WorldService worldService;
    private final ModuleService moduleService = new ModuleService(this);
    private final PlayerStateStore playerStateStore = new PlayerStateStore();
    private final GameItemService itemService = new GameItemService();
    private final PlayerEvents playerEvents = new PlayerEvents(playerStateStore);
    private GlowService glowService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fileService = new FileService(this);
        mapService = new MapService(fileService);
        worldService = new WorldService(this, playerStateStore);
        playerEvents.setWorldService(worldService);
        fileService.reload();
        glowService = new GlowService(this);
        glowService.enable();

        getServer().getPluginManager().registerEvents(playerEvents, this);
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerStateStore.save(
                player.getUniqueId(),
                worldService.getPlayerStateId(player.getWorld()),
                playerEvents.savePlayerState(player)
            );
        }
        fileService.closeModuleLoaders();
        worldService.unloadAll();
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
    public GameItemService itemService() {
        return itemService;
    }
    public GlowService glowService() {
        if (glowService == null) {
            throw new IllegalStateException("GlowService is not available before plugin enable.");
        }
        return glowService;
    }
}
