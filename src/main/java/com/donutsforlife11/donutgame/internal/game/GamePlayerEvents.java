package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

public class GamePlayerEvents implements Listener {
    private final ModuleService moduleService;

    public GamePlayerEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void playerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = moduleService.getGameOfPlayer(player).playerManager();
        if (!playerManager.isSpectator(player)) {
            playerManager.setSpectator(player);
        }
    }

    @EventHandler
    public void playerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = moduleService.getGameOfPlayer(player).playerManager();
        Location spawnpoint = playerManager.getPlayerSpawn(player);
        if (spawnpoint != null) {
            event.setRespawnLocation(spawnpoint.clone());
        }
    }
}
