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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.item.GameItemComponents.PlayerVisibilityMode;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

public class GameItemComponentEvents implements Listener {
    private static final String VANILLA_PROJECTILE_KEY = "vanilla_projectile";
    private static final double SNOWBALL_DAMAGE = 0.33;
    private static final double EGG_KNOCKBACK_DAMAGE = 0.0001;

    private final ModuleService moduleService;
    private final Map<UUID, PendingAutoIgnite> pendingAutoIgnites = new LinkedHashMap<>();
    private final Map<UUID, PlayerVisibilityMode> visibilityModes = new LinkedHashMap<>();
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
        if (event.getHand() == null || !isUseAction(event.getAction())) {
            return;
        }
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItem();
        if (player == null || item == null) {
            return;
        }
        GameItemComponents.normalize(player, item);
        if (GameItemComponents.hasPlayerVisibilityToggle(item)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            togglePlayerVisibility(player, event.getPlayer(), item, event.getHand());
            syncInventory(event.getPlayer());
            return;
        }
        if (GameItemComponents.hasInfiniteUse(item)) {
            scheduleInfiniteUseReplenish(event.getPlayer(), event.getHand(), item.clone());
        }
        int fuse = GameItemComponents.autoIgniteFuse(item);
        EntityType entityType = spawnType(item.getType());
        if (fuse <= 0 || entityType == null || !canAutoIgnite(entityType)) {
            return;
        }
        pendingAutoIgnites.put(player.uuid(), new PendingAutoIgnite(player.uuid(), entityType, fuse));
        Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> pendingAutoIgnites.remove(player.uuid()), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (gamePlayer(player) == null) {
            return;
        }
        ItemStack item = launchedProjectileItem(player, event.getEntity());
        if (GameItemComponents.hasVanillaProjectile(item)) {
            event.getEntity().getPersistentDataContainer().set(vanillaProjectileKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball) && !(projectile instanceof Egg)) {
            return;
        }
        if (isVanillaProjectile(projectile)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (gamePlayer(player) == null) {
            return;
        }

        DamageSource damageSource = projectileDamageSource(projectile, player);
        if (projectile instanceof Snowball) {
            target.damage(SNOWBALL_DAMAGE, damageSource);
        } else {
            target.damage(EGG_KNOCKBACK_DAMAGE, damageSource);
        }
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
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (GameItemComponents.hasUndroppable(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
            return;
        }
        if (GameItemComponents.hasInfiniteBuild(event.getItemDrop().getItemStack())) {
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItem();
        if (player == null || item == null || !GameItemComponents.hasInfiniteUse(item)) {
            return;
        }
        ItemStack replacement = GameItemComponents.normalize(player, item.clone());
        event.setReplacement(replacement);
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(event.getPlayer()));
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
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> {
            syncInventory(event.getPlayer());
            refreshPlayerVisibilityForAll();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visibilityModes.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(moduleService.plugin(), this::refreshPlayerVisibilityForAll);
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
        if (gamePlayer != null && gamePlayer.isSpectator()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack item = inventory.getItem(index);
            if (item != null) {
                inventory.setItem(index, GameItemComponents.normalize(gamePlayer, item));
            }
        }
        player.updateInventory();
    }

    private void scheduleInfiniteUseReplenish(Player player, EquipmentSlot hand, ItemStack usedItem) {
        int originalAmount = usedItem.getAmount();
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> replenishUsedItem(player, hand, usedItem, originalAmount));
        Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> replenishUsedItem(player, hand, usedItem, originalAmount), 5L);
    }

    private void replenishUsedItem(Player player, EquipmentSlot hand, ItemStack usedItem, int originalAmount) {
        if (player == null || !player.isOnline() || hand == null || usedItem == null || originalAmount <= 0) {
            return;
        }
        ItemStack current = itemInHand(player, hand);
        if (!wasDepleted(usedItem, originalAmount, current)) {
            return;
        }
        ItemStack replacement = usedItem.clone();
        replacement.setAmount(originalAmount);
        setItemInHand(player, hand, replacement);
        syncInventory(player);
    }

    private boolean wasDepleted(ItemStack usedItem, int originalAmount, ItemStack current) {
        if (current == null || current.getType().isAir()) {
            return true;
        }
        if (!current.isSimilar(usedItem)) {
            return originalAmount == 1;
        }
        return current.getAmount() < originalAmount;
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    private void setItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
        player.updateInventory();
    }

    private void togglePlayerVisibility(GamePlayer gamePlayer, Player bukkitPlayer, ItemStack item, EquipmentSlot hand) {
        PlayerVisibilityMode current = GameItemComponents.playerVisibilityMode(item);
        PlayerVisibilityMode next = nextVisibilityMode(gamePlayer, current);
        GameItemComponents.setPlayerVisibilityMode(item, next);
        setItemInHand(bukkitPlayer, hand, item);
        visibilityModes.put(gamePlayer.uuid(), next);
        applyPlayerVisibility(gamePlayer, bukkitPlayer, next);
    }

    private PlayerVisibilityMode nextVisibilityMode(GamePlayer gamePlayer, PlayerVisibilityMode current) {
        boolean teamModeAvailable = teamModeAvailable(gamePlayer);
        if (teamModeAvailable) {
            return switch (current) {
                case EVERYONE -> PlayerVisibilityMode.TEAM_ONLY;
                case TEAM_ONLY -> PlayerVisibilityMode.SELF_ONLY;
                case SELF_ONLY -> PlayerVisibilityMode.EVERYONE;
            };
        }
        return current == PlayerVisibilityMode.EVERYONE ? PlayerVisibilityMode.SELF_ONLY : PlayerVisibilityMode.EVERYONE;
    }

    private boolean teamModeAvailable(GamePlayer gamePlayer) {
        if (gamePlayer == null) {
            return false;
        }
        for (GameTeam team : gamePlayer.module().teamManager().getTeams()) {
            if (team.getPlayers().size() > 1) {
                return true;
            }
        }
        return false;
    }

    private void refreshPlayerVisibilityForAll() {
        for (GameModule game : moduleService.activeGames().values()) {
            for (GamePlayer viewer : game.playerManager().getOnlinePlayers()) {
                Player bukkitViewer = viewer.bukkitPlayer();
                if (bukkitViewer == null) {
                    continue;
                }
                PlayerVisibilityMode mode = visibilityModes.getOrDefault(viewer.uuid(), PlayerVisibilityMode.EVERYONE);
                applyPlayerVisibility(viewer, bukkitViewer, mode);
            }
        }
    }

    private void applyPlayerVisibility(GamePlayer viewer, Player bukkitViewer, PlayerVisibilityMode mode) {
        GameModule game = viewer.module();
        GameTeam viewerTeam = viewer.getTeam();
        for (GamePlayer target : game.playerManager().getOnlinePlayers()) {
            Player bukkitTarget = target.bukkitPlayer();
            if (bukkitTarget == null) {
                continue;
            }
            if (target == viewer) {
                bukkitViewer.showEntity(moduleService.plugin(), bukkitTarget);
                continue;
            }
            boolean visible = switch (mode) {
                case EVERYONE -> !target.isSpectator();
                case TEAM_ONLY -> !target.isSpectator() && viewerTeam != null && viewerTeam == target.getTeam();
                case SELF_ONLY -> false;
            };
            if (visible) {
                bukkitViewer.showEntity(moduleService.plugin(), bukkitTarget);
            } else {
                bukkitViewer.hideEntity(moduleService.plugin(), bukkitTarget);
            }
            moduleService.plugin().spectatorService().refreshPlayerList(bukkitViewer, bukkitTarget);
        }
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

    private ItemStack launchedProjectileItem(Player player, Projectile projectile) {
        Material material = projectile instanceof Snowball ? Material.SNOWBALL : projectile instanceof Egg ? Material.EGG : null;
        if (material == null) {
            return null;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == material) {
            return mainHand;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return offHand.getType() == material ? offHand : null;
    }

    private boolean isVanillaProjectile(Projectile projectile) {
        return projectile.getPersistentDataContainer().has(vanillaProjectileKey(), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private org.bukkit.NamespacedKey vanillaProjectileKey() {
        return new org.bukkit.NamespacedKey(moduleService.plugin(), VANILLA_PROJECTILE_KEY);
    }

    private DamageSource projectileDamageSource(Projectile projectile, Player shooter) {
        DamageSource.Builder builder = DamageSource.builder(DamageType.ARROW)
            .withDirectEntity(projectile);
        if (shooter != null) {
            builder.withCausingEntity(shooter);
        }
        return builder.build();
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
