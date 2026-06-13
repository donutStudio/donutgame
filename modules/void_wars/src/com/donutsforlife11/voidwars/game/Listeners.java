package com.donutsforlife11.voidwars.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.voidwars.VoidWars;

public class Listeners implements Listener {
    private final VoidWars game;
    private final PlayerManager playerManager;

    public Listeners(VoidWars game) {
        this.game = game;
        this.playerManager = game.playerManager();
    }

    @EventHandler public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!playerManager.isRegistered(player)) {
            return;
        }
        if (!game.gameStarted()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!playerManager.isRegistered(player)) {
            return;
        }
        if (game.gameStarted()) {
            Players.tryRespawn(player, game.config().getInt("base_respawn_time"));
        } else {
            playerManager.respawnPlayer(player);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerManager.isRegistered(player)) {
            return;
        }
        if (game.gameStarted()) {
            Players.tryRespawn(player, game.config().getInt("base_respawn_time"));
        }
    }
}
