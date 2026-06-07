package com.donutsforlife11.donutgame.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.game.ModuleManager;

public class GamePlayerEvents implements Listener {
    private final ModuleManager moduleManager;

    public GamePlayerEvents(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @EventHandler
    public void playerRespawn(PlayerRespawnEvent event) {
        moduleManager.handlePlayerRespawn(event.getPlayer(), event);
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        moduleManager.handlePlayerDisconnect(event.getPlayer());
    }
}
