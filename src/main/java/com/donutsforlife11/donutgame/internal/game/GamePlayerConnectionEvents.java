package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class GamePlayerConnectionEvents implements Listener {
    private final ModuleService moduleService;

    public GamePlayerConnectionEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game != null) {
            game.playerManager().markOffline(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        GameModule game = moduleService.getOfflineGameOfPlayer(event.getPlayer().getUniqueId());
        if (game != null) {
            game.playerManager().join(event.getPlayer()).exceptionally(throwable -> {
                game.logError("Failed to reattach player " + event.getPlayer().getName() + " after reconnect.", throwable);
                return false;
            });
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GameModule game = moduleService.getGameOfPlayer(player);
        if (game == null || game.world().hungerEnabled()) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
    }
}
