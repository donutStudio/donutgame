package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;

public class GamePlayerEvents implements Listener {
    private final ModuleService moduleService;

    public GamePlayerEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void playerDeath(PlayerDeathEvent event) {
    }

    @EventHandler
    public void playerRespawn(PlayerRespawnEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) return;
        if (gamePlayer.respawnLocation() != null) event.setRespawnLocation(gamePlayer.respawnLocation().toBukkit(gamePlayer.world().bukkitWorld()));
        GameModule game = game(event.getPlayer());
        moduleService.plugin().getServer().getScheduler().runTask(moduleService.plugin(), () -> {
            if (gamePlayer.isSpectator()) gamePlayer.syncSpectatorState();
            if (game != null) game.uiManager().refreshPlayerStateAfterTrackingReset();
        });
    }

    @EventHandler
    public void playerChangedWorld(PlayerChangedWorldEvent event) {
        GameModule game = game(event.getPlayer());
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        moduleService.plugin().getServer().getScheduler().runTask(moduleService.plugin(), () -> {
            if (gamePlayer != null && gamePlayer.isSpectator()) gamePlayer.syncSpectatorState();
            if (game != null) game.uiManager().refreshPlayerStateAfterTrackingReset();
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        GameModule game = game(event.getPlayer());
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) gamePlayer.setSpectator(gamePlayer.location());
        if (game != null) game.uiManager().refreshPlayerState();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer) || !(event.getDamager() instanceof Player attacker)) return;
        GamePlayer target = gamePlayer(targetPlayer);
        GamePlayer source = gamePlayer(attacker);
        if (target == null || source == null) return;
        GameTeam sourceTeam = source.team();
        if (sourceTeam != null && sourceTeam == target.team() && !sourceTeam.friendlyFire()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null || !gamePlayer.isSpectator()) return;
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            GameLocation fallback = gamePlayer.respawnLocation() != null ? gamePlayer.respawnLocation() : gamePlayer.world().worldSpawn();
            gamePlayer.teleport(fallback);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            GamePlayer gamePlayer = gamePlayer(player);
            if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
        }
    }

    @EventHandler
    public void playerKillIndicator(PlayerDeathEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker)) {
            return;
        }
        GameModule attackerGame = game(attacker);
        if (attackerGame == null) {
            return;
        }
        GamePlayer attackerPlayer = attackerGame.playerManager().getPlayer(attacker);
        if (attackerPlayer == null) {
            return;
        }
        Player target = event.getPlayer();
        var team = target.getScoreboard().getPlayerTeam(target);
        attackerGame.uiManager().playSound(attackerPlayer, Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.15f);
        // Don't change this subtitle, I like it and the icon renders just fine
        attackerGame.uiManager().subtitle(attackerPlayer, Component.text()
            .append(Component.text("🗡 "))
            .append(Component.text(target.getName() + " ", team == null ? NamedTextColor.WHITE : team.color()))
            .append(Component.object(ObjectContents.playerHead(target)))
            .build()
        );
    }

    private GamePlayer gamePlayer(Player player) {
        GameModule game = game(player);
        return game == null ? null : game.playerManager().getPlayer(player);
    }

    private GameModule game(Player player) {
        try {
            return moduleService.getGameOfPlayer(player);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
