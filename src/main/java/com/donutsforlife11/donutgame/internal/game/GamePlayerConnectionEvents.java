package com.donutsforlife11.donutgame.internal.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

public class GamePlayerConnectionEvents implements Listener {
    private final ModuleService moduleService;
    private final Map<UUID, GameLocation> pendingSpectatorRespawns = new HashMap<>();

    public GamePlayerConnectionEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        for (GameModule game : moduleService.activeGames().values()) {
            game.playerManager().detachAfterExternalWorldChange(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game != null) {
            game.playerManager().markOffline(event.getPlayer());
        }
        pendingSpectatorRespawns.remove(event.getPlayer().getUniqueId());
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
        moduleService.plugin().getServer().getScheduler().runTask(
            moduleService.plugin(),
            () -> moduleService.plugin().spectatorService().refreshForViewer(event.getPlayer())
        );
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game == null) {
            return;
        }
        GamePlayer player = game.playerManager().getPlayer(event.getPlayer());
        if (player != null) {
            player.rememberVanillaDeath();
        }
        pendingSpectatorRespawns.put(
            event.getPlayer().getUniqueId(),
            spectatorRespawnLocation(game, event.getPlayer(), player)
        );
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        GameLocation deathLocation = pendingSpectatorRespawns.get(event.getPlayer().getUniqueId());
        if (deathLocation == null) {
            return;
        }
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game == null) {
            return;
        }
        if (game.world() != null && game.world().bukkitWorld() != null) {
            event.setRespawnLocation(deathLocation.toBukkit(game.world().bukkitWorld()));
        }
    }

    @EventHandler
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        GameLocation deathLocation = pendingSpectatorRespawns.remove(event.getPlayer().getUniqueId());
        if (deathLocation == null) {
            return;
        }
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game == null) {
            return;
        }
        GamePlayer player = game.playerManager().getPlayer(event.getPlayer());
        if (player != null) {
            player.enterPostDeathSpectator(deathLocation);
        }
    }

    private GameLocation spectatorRespawnLocation(GameModule game, Player bukkitPlayer, GamePlayer gamePlayer) {
        if (game.world() != null && game.world().bukkitWorld() != null && bukkitPlayer.getLocation().getY() < game.world().bukkitWorld().getMinHeight()) {
            return gamePlayer == null ? game.world().worldSpawn() : gamePlayer.spawnPoint();
        }
        return new GameLocation(game.world(), bukkitPlayer.getLocation());
    }
}
