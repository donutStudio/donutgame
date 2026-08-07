package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.api.player.GamePlayer;

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
        GamePlayer gamePlayer = moduleService.getGameOfPlayer(player).playerManager().getPlayer(player);
        if (gamePlayer != null && !gamePlayer.isSpectator()) {
            gamePlayer.setSpectator();
        }
    }

    @EventHandler
    public void playerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GamePlayer gamePlayer = moduleService.getGameOfPlayer(player).playerManager().getPlayer(player);
        if (gamePlayer != null && gamePlayer.respawnLocation() != null) {
            event.setRespawnLocation(gamePlayer.respawnLocation().toBukkit(gamePlayer.world().bukkitWorld()));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GamePlayer gamePlayer = moduleService.getGameOfPlayer(player).playerManager().getPlayer(player);
        if (gamePlayer != null && !gamePlayer.isSpectator()) {
            gamePlayer.setSpectator();
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
