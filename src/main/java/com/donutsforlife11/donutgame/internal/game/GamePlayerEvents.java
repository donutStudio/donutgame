package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;

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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = moduleService.getGameOfPlayer(player).playerManager();
        if (!playerManager.isSpectator(player)) {
            playerManager.setSpectator(player);
        }
    }

    @EventHandler
    public void playerKillIndicator(PlayerDeathEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker)) {
            return;
        }
        Player target = event.getPlayer();
        moduleService.getGameOfPlayer(attacker).uiManager().subtitle(attacker, Component.text("🗡 ")
            .append(Component.text(target.getName() + " ", target.getScoreboard().getPlayerTeam(target).color()))
            .append(Component.object(ObjectContents.playerHead(target)))
        );
    }
}
