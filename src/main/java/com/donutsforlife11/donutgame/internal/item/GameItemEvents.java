package com.donutsforlife11.donutgame.internal.item;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.ModuleService;

public class GameItemEvents implements Listener {
    private final ModuleService moduleService;
    private final GameItemService itemService;
    private final Map<UUID, PendingAutoIgnite> pendingAutoIgnites = new LinkedHashMap<>();

    public GameItemEvents(ModuleService moduleService, GameItemService itemService) {
        this.moduleService = moduleService;
        this.itemService = itemService;
        Bukkit.getScheduler().runTaskTimer(moduleService.plugin(), this::normalizeActiveInventories, 1L, 1L);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        if (player == null) {
            return;
        }
        ItemStack item = event.getItemInHand();
        int fuse = itemService.autoIgniteFuse(item);
        if (fuse > 0 && item.getType() == Material.TNT) {
            event.getBlockPlaced().setType(Material.AIR, false);
            Location location = event.getBlockPlaced().getLocation().add(0.5, 0.0, 0.5);
            event.getBlockPlaced().getWorld().spawn(
                location,
                TNTPrimed.class,
                tnt -> {
                    tnt.setSource(event.getPlayer());
                    tnt.setFuseTicks(fuse);
                    tnt.setVelocity(new Vector());
                }
            );
        }
        itemService.replenishPlacedBlock(player, item, event.getHand());
        itemService.normalizeInventory(player);
        Player bukkitPlayer = player.player();
        if (bukkitPlayer != null) {
            bukkitPlayer.updateInventory();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItem();
        if (player == null || item == null) {
            return;
        }
        int fuse = itemService.autoIgniteFuse(item);
        EntityType entityType = spawnType(item.getType());
        if (fuse <= 0 || entityType == null) {
            return;
        }
        pendingAutoIgnites.put(player.uuid(), new PendingAutoIgnite(player.uuid(), entityType, fuse));
        Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> pendingAutoIgnites.remove(player.uuid()), 2L);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (pendingAutoIgnites.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        for (Iterator<PendingAutoIgnite> iterator = pendingAutoIgnites.values().iterator(); iterator.hasNext();) {
            PendingAutoIgnite pending = iterator.next();
            Player player = Bukkit.getPlayer(pending.playerId());
            if (player == null || player.getWorld() != entity.getWorld() || entity.getType() != pending.entityType()) {
                continue;
            }
            if (player.getLocation().distanceSquared(entity.getLocation()) > 36) {
                continue;
            }
            ignite(entity, player, pending.fuse());
            iterator.remove();
            break;
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    @EventHandler
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> itemService.normalizeInventory(gamePlayer));
    }

    private void ignite(Entity entity, Player player, int fuse) {
        if (entity instanceof Creeper creeper) {
            creeper.setMaxFuseTicks(fuse);
            creeper.setFuseTicks(0);
            creeper.ignite(player);
            return;
        }
        if (entity instanceof TNTPrimed tnt) {
            tnt.setSource(player);
            tnt.setFuseTicks(fuse);
            tnt.setVelocity(new Vector());
            return;
        }
        if (entity instanceof EnderCrystal crystal) {
            Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> {
                if (!crystal.isValid()) {
                    return;
                }
                crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, false, true, player);
                crystal.remove();
            }, fuse);
        }
    }

    private EntityType spawnType(Material material) {
        String name = material.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return "TNT".equals(name) ? EntityType.TNT : null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private GamePlayer gamePlayer(Player player) {
        try {
            return moduleService.getGameOfPlayer(player).playerManager().getPlayer(player);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void normalizeActiveInventories() {
        for (var game : moduleService.activeGames().values()) {
            for (GamePlayer player : game.playerManager().getPlayers()) {
                if (player.isOnline()) {
                    itemService.normalizeInventory(player);
                }
            }
        }
    }

    private record PendingAutoIgnite(UUID playerId, EntityType entityType, int fuse) {
    }
}
