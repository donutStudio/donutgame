package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;

public class GamePlayerEvents implements Listener {
    private final ModuleService moduleService;

    public GamePlayerEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
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
        if (game != null) game.playerManager().handlePlayerJoin(event.getPlayer());
        if (game != null) game.uiManager().refreshPlayerState();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorInteract(PlayerInteractEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null || !gamePlayer.isSpectator() || isSpectatorCompass(event.getItem())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorBlockPlace(BlockPlaceEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorBlockBreak(BlockBreakEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorAttemptPickup(PlayerAttemptPickupItemEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) {
            event.setFlyAtPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorPickupExperience(PlayerPickupExperienceEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectatorExperience(PlayerExpChangeEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setAmount(0);
    }

    @EventHandler
    public void onSpectatorEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        GameModule game = game(player);
        if (gamePlayer != null && gamePlayer.isSpectator() && game != null) {
            moduleService.plugin().getServer().getScheduler().runTask(moduleService.plugin(), () -> game.uiManager().refreshPlayerState());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) return;
        Player attacker = attackingPlayer(event);
        if (attacker == null || attacker == targetPlayer) return;
        GamePlayer target = gamePlayer(targetPlayer);
        GamePlayer source = gamePlayer(attacker);
        if (target == null || source == null) return;
        GameTeam sourceTeam = source.team();
        if (sourceTeam != null && sourceTeam == target.team() && !sourceTeam.allowsFriendlyFireDamage(event.getDamageSource().getDamageType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onThrowableHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball || projectile instanceof Egg)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        GameModule game = game(projectile);
        if (game == null) return;
        if (target instanceof Player player) {
            GamePlayer targetPlayer = game.playerManager().getPlayer(player);
            if (targetPlayer != null && targetPlayer.isSpectator()) return;
        }
        Entity shooter = shooter(projectile);
        if (isBlockedFriendlyThrowable(game, shooter, target)) {
            event.setCancelled(true);
            projectile.remove();
            return;
        }
        event.setCancelled(true);
        if (target instanceof Damageable damageable) damageThrowable(projectile, shooter, target, damageable);
        applyThrowableKnockback(projectile, target);
        projectile.remove();
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
        if (!(event.getEntity() instanceof Player player)) return;
        GameModule game = game(player);
        if (game == null || !game.world().contains(player)) return;
        GamePlayer gamePlayer = game.playerManager().getPlayer(player);
        if (gamePlayer != null && (gamePlayer.isSpectator() || !game.world().hungerEnabled())) event.setCancelled(true);
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
        attackerGame.uiManager().playSound(attackerPlayer, Sound.ITEM_TRIDENT_RETURN, 1.3f, 0.35f);
        attackerGame.uiManager().playSound(attackerPlayer, Sound.ITEM_TRIDENT_RETURN, 1.3f, 1.15f);
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

    private GameModule game(Entity entity) {
        if (entity instanceof Player player) {
            return game(player);
        }
        for (GameModule game : moduleService.activeGames().values()) {
            if (game.world() != null && game.world().contains(entity)) return game;
        }
        return null;
    }

    private GameModule game(Player player) {
        try {
            return moduleService.getGameOfPlayer(player);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Player attackingPlayer(EntityDamageEvent event) {
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }
        Entity directEntity = event.getDamageSource().getDirectEntity();
        if (directEntity instanceof Player player) {
            return player;
        }
        if (directEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Entity shooter(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Entity entity ? entity : null;
    }

    private boolean isBlockedFriendlyThrowable(GameModule game, Entity shooter, LivingEntity target) {
        if (!(shooter instanceof Player attacker) || !(target instanceof Player targetPlayer)) return false;
        GamePlayer source = game.playerManager().getPlayer(attacker);
        GamePlayer victim = game.playerManager().getPlayer(targetPlayer);
        if (source == null || victim == null || source == victim) return false;
        GameTeam team = source.team();
        return team != null && team == victim.team() && !team.allowsFriendlyFireDamage(DamageType.THROWN);
    }

    private void damageThrowable(Projectile projectile, Entity shooter, LivingEntity target, Damageable damageable) {
        DamageSource.Builder source = DamageSource.builder(DamageType.THROWN).withDirectEntity(projectile);
        if (shooter != null) source.withCausingEntity(shooter);
        target.setNoDamageTicks(0);
        damageable.damage(projectile instanceof Egg ? 0.01 : 0.5, source.build());
        target.setNoDamageTicks(0);
    }

    private void applyThrowableKnockback(Projectile projectile, LivingEntity target) {
        Vector direction = projectile.getVelocity();
        if (direction.lengthSquared() < 0.001) {
            direction = target.getLocation().toVector().subtract(projectile.getLocation().toVector());
        }
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(0.5);
        direction.setY(Math.max(0.22, direction.getY() + 0.12));
        target.setVelocity(target.getVelocity().add(direction));
    }

    private boolean isSpectatorCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && GamePlayer.isSpectatorCompass(item);
    }
}
