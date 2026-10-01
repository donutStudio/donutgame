package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class SpectatorGuardEvents implements Listener {
    private final ModuleService moduleService;

    public SpectatorGuardEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventBlockBreak(BlockBreakEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventBlockPlace(BlockPlaceEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventBucketFill(PlayerBucketFillEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventInteract(PlayerInteractEvent event) {
        if (!isSpectator(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventPhysicalInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventEntityInteract(PlayerInteractEntityEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventContainerOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof SpectatorMenuEvents.MenuHolder) {
            return;
        }
        if (event.getPlayer() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SpectatorMenuEvents.MenuHolder) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player && isSpectator(player) && !isHotbarSelection(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpectatorMenuEvents.MenuHolder) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventSwapHands(PlayerSwapHandItemsEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventItemDrop(PlayerDropItemEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFallDistance(0.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventAttacks(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void rescueSpectatorsFromVoid(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GamePlayer spectator = spectator(player);
        if (spectator == null) {
            return;
        }
        player.setFallDistance(0.0f);
        World world = player.getWorld();
        if (event.getTo().getY() >= world.getMinHeight()) {
            return;
        }
        GameLocation spawnPoint = spectator.spawnPoint();
        Location destination = spawnPoint == null ? world.getSpawnLocation() : spawnPoint.toBukkit(world);
        player.teleport(destination);
    }

    private boolean isSpectator(Player player) {
        return spectator(player) != null;
    }

    private boolean isHotbarSelection(InventoryClickEvent event) {
        return event.getClick() == ClickType.NUMBER_KEY
            || event.getAction() == InventoryAction.HOTBAR_SWAP;
    }

    private GamePlayer spectator(Player player) {
        GameModule game = moduleService.getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        return gamePlayer != null && gamePlayer.isSpectator() ? gamePlayer : null;
    }
}
