package com.donutsforlife11.voidwars;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

public class VoidWarsEvents implements Listener {
    private final VoidWars game;
    private final PlayerManager playerManager;

    public VoidWarsEvents(VoidWars game) {
        this.game = game;
        this.playerManager = game.playerManager();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!playerManager.isRegistered(player)) {
            return;
        }

        if (!game.gameStarted()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!playerManager.isRegistered(player)) {
            return;
        }

        if (game.gameStarted()) {
            playerManager.setSpectator(player);
        } else {
            playerManager.respawnPlayer(player);
        }
    }
}
