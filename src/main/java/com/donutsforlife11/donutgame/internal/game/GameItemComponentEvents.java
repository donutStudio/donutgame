package com.donutsforlife11.donutgame.internal.game;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class GameItemComponentEvents implements Listener {
    private final ModuleService moduleService;
    private final Map<UUID, PendingAutoIgnite> pendingAutoIgnites = new LinkedHashMap<>();
    private final Set<BlockKey> infinitePlacedBlocks = new HashSet<>();

    public GameItemComponentEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItemInHand();
        if (player == null || item == null) {
            return;
        }
        GameItemComponents.normalize(player, item);
        ItemStack replacement = item.clone();
        int fuse = GameItemComponents.autoIgniteFuse(item);
        boolean autoIgniteBlock = fuse > 0 && item.getType().isBlock();
        if (autoIgniteBlock) {
            spawnBlockTnt(event.getBlockPlaced(), event.getPlayer(), item.getType(), fuse);
        }
        if (GameItemComponents.hasInfiniteBuild(item)) {
            if (!autoIgniteBlock) {
                infinitePlacedBlocks.add(BlockKey.of(event.getBlockPlaced()));
            }
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> {
                replenishPlacedBlock(event.getPlayer(), replacement, event.getHand());
                syncInventory(event.getPlayer());
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (infinitePlacedBlocks.remove(BlockKey.of(event.getBlock())) || hasInfiniteBuildItem(event.getPlayer(), event.getBlock().getType())) {
            event.setDropItems(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isUseAction(event.getAction())) {
            return;
        }
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItem();
        if (player == null || item == null) {
            return;
        }
        GameItemComponents.normalize(player, item);
        int fuse = GameItemComponents.autoIgniteFuse(item);
        EntityType entityType = spawnType(item.getType());
        if (fuse <= 0 || entityType == null || !canAutoIgnite(entityType)) {
            return;
        }
        pendingAutoIgnites.put(player.uuid(), new PendingAutoIgnite(player.uuid(), entityType, fuse));
        Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> pendingAutoIgnites.remove(player.uuid()), 2L);
    }

    @EventHandler(ignoreCancelled = true)
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

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack pickedUp = event.getItem().getItemStack();
        if (pickedUp != null && hasInfiniteBuildItem(player, pickedUp.getType())) {
            event.setCancelled(true);
            event.getItem().remove();
            syncInventory(player);
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (GameItemComponents.hasInfiniteBuild(event.getItemDrop().getItemStack())) {
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(player));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(player));
        }
    }

    @EventHandler
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(player));
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
    }

    private void spawnBlockTnt(Block block, Player player, Material material, int fuse) {
        block.setType(Material.AIR, false);
        Location location = block.getLocation().add(0.5, 0.0, 0.5);
        block.getWorld().spawn(location, TNTPrimed.class, tnt -> {
            tnt.setSource(player);
            tnt.setFuseTicks(fuse);
            tnt.setVelocity(new Vector());
            tnt.setBlockData(material.createBlockData());
        });
    }

    private void replenishPlacedBlock(Player player, ItemStack replacement, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        if (hand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(replacement);
        } else {
            inventory.setItemInMainHand(replacement);
        }
        player.updateInventory();
    }

    private void syncInventory(Player player) {
        GamePlayer gamePlayer = gamePlayer(player);
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack item = inventory.getItem(index);
            if (item != null) {
                inventory.setItem(index, GameItemComponents.normalize(gamePlayer, item));
            }
        }
        player.updateInventory();
    }

    private boolean hasInfiniteBuildItem(Player player, Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && GameItemComponents.hasInfiniteBuild(item)) {
                return true;
            }
        }
        return false;
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
        if (material == Material.TNT) {
            return EntityType.TNT;
        }
        if (material == Material.END_CRYSTAL) {
            return EntityType.END_CRYSTAL;
        }
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean canAutoIgnite(EntityType entityType) {
        return entityType == EntityType.CREEPER || entityType == EntityType.TNT || entityType == EntityType.END_CRYSTAL;
    }

    private boolean isUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private GamePlayer gamePlayer(Player player) {
        GameModule game = moduleService.getGameOfPlayer(player);
        return game == null ? null : game.playerManager().getPlayer(player);
    }

    private record PendingAutoIgnite(UUID playerId, EntityType entityType, int fuse) {
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
