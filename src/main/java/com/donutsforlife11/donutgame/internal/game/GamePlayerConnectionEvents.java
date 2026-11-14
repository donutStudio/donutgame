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
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.api.player.PlayerManagerHandle;
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
            PlayerManagerHandle.detachAfterExternalWorldChange(game.playerManager(), event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameModule game = moduleService.getGameOfPlayer(event.getPlayer());
        if (game != null) {
            PlayerManagerHandle.markOffline(game.playerManager(), event.getPlayer());
        }
        pendingSpectatorRespawns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        GameModule lockedGame = moduleService.lockedGame();
        if (lockedGame != null) {
            moduleService.joinLockedGame(event.getPlayer()).exceptionally(throwable -> {
                lockedGame.logError("Failed to join player " + event.getPlayer().getName() + " to locked game " + lockedGame.index() + ".", throwable);
                return false;
            });
            scheduleSpectatorRefresh(event.getPlayer());
            return;
        }
        GameModule game = moduleService.getOfflineGameOfPlayer(event.getPlayer().getUniqueId());
        if (game != null) {
            PlayerManagerHandle.joinInitial(game.playerManager(), event.getPlayer()).exceptionally(throwable -> {
                game.logError("Failed to reattach player " + event.getPlayer().getName() + " after reconnect.", throwable);
                return false;
            });
        }
        scheduleSpectatorRefresh(event.getPlayer());
    }

    private void scheduleSpectatorRefresh(Player player) {
        moduleService.plugin().getServer().getScheduler().runTask(
            moduleService.plugin(),
            () -> moduleService.plugin().spectatorService().refreshForViewer(player)
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
        GamePlayer player = PlayerManagerHandle.getPlayer(game.playerManager(), event.getPlayer());
        if (player != null) {
            GamePlayerHandle.rememberVanillaDeath(player);
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
        GamePlayer player = PlayerManagerHandle.getPlayer(game.playerManager(), event.getPlayer());
        GameLocation fallback = GameLocationHandle.isCurrent(deathLocation) ? deathLocation : game.world().getSpawnLocation();
        GameLocation respawnLocation = player == null ? fallback : GamePlayerHandle.postDeathRespawnLocation(player, fallback);
        org.bukkit.World bukkitWorld = GameWorldHandle.bukkitWorld(game.world());
        if (bukkitWorld != null) {
            event.setRespawnLocation(GameLocationHandle.toBukkit(respawnLocation, game.world()));
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
        GamePlayer player = PlayerManagerHandle.getPlayer(game.playerManager(), event.getPlayer());
        if (player != null) {
            GamePlayerHandle.enterPostDeathSpectator(player, GameLocationHandle.isCurrent(deathLocation) ? deathLocation : player.spawnPoint());
        }
    }

    private GameLocation spectatorRespawnLocation(GameModule game, Player bukkitPlayer, GamePlayer gamePlayer) {
        org.bukkit.World bukkitWorld = GameWorldHandle.bukkitWorld(game.world());
        if (bukkitWorld != null && bukkitPlayer.getLocation().getY() < bukkitWorld.getMinHeight()) {
            return gamePlayer == null ? game.world().getSpawnLocation() : gamePlayer.spawnPoint();
        }
        return GameLocationHandle.create(game.world(), bukkitPlayer.getLocation());
    }
}
