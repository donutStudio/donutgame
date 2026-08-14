package com.donutsforlife11.donutgame.internal.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.ModuleService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameItemEvents implements Listener {
    private final ModuleService moduleService;
    private final GameItemService itemService;
    private final Map<UUID, PendingAutoIgnite> pendingAutoIgnites = new LinkedHashMap<>();
    private final Set<BlockKey> infinitePlacedBlocks = new HashSet<>();

    public GameItemEvents(ModuleService moduleService, GameItemService itemService) {
        this.moduleService = moduleService;
        this.itemService = itemService;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        if (player == null) return;
        GameModule game = game(event.getPlayer());
        if (game.isTransitioning()) return;
        ItemStack item = event.getItemInHand();
        int fuse = itemService.autoIgniteFuse(item);
        if (fuse > 0 && item.getType() == Material.TNT) {
            event.getBlockPlaced().setType(Material.AIR, false);
            Location location = event.getBlockPlaced().getLocation().add(0.5, 0.0, 0.5);
            event.getBlockPlaced().getWorld().spawn(location, TNTPrimed.class, tnt -> {
                tnt.setSource(event.getPlayer());
                tnt.setFuseTicks(fuse);
                tnt.setVelocity(new Vector());
            });
        }
        if (!itemService.hasInfiniteBuild(item)) return;
        ItemStack replacement = item.clone();
        infinitePlacedBlocks.add(BlockKey.of(event.getBlockPlaced()));
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> {
            itemService.replenishPlacedBlock(player, replacement, event.getHand());
            syncInventory(player);
            Player bukkitPlayer = player.player();
            if (bukkitPlayer != null) bukkitPlayer.updateInventory();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (infinitePlacedBlocks.remove(BlockKey.of(event.getBlock()))) event.setDropItems(false);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        GamePlayer player = gamePlayer(event.getPlayer());
        ItemStack item = event.getItem();
        if (player == null || item == null) return;
        if (player.isSpectator() && GamePlayer.isSpectatorCompass(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            openMainSpectatorMenu(player);
            return;
        }
        int fuse = itemService.autoIgniteFuse(item);
        EntityType entityType = spawnType(item.getType());
        if (fuse <= 0 || entityType == null) return;
        pendingAutoIgnites.put(player.uuid(), new PendingAutoIgnite(player.uuid(), entityType, fuse));
        Bukkit.getScheduler().runTaskLater(moduleService.plugin(), () -> pendingAutoIgnites.remove(player.uuid()), 2L);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (pendingAutoIgnites.isEmpty()) return;
        Entity entity = event.getEntity();
        for (Iterator<PendingAutoIgnite> iterator = pendingAutoIgnites.values().iterator(); iterator.hasNext();) {
            PendingAutoIgnite pending = iterator.next();
            Player player = Bukkit.getPlayer(pending.playerId());
            if (player == null || player.getWorld() != entity.getWorld() || entity.getType() != pending.entityType()) continue;
            if (player.getLocation().distanceSquared(entity.getLocation()) > 36) continue;
            ignite(entity, player, pending.fuse());
            iterator.remove();
            break;
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) return;
        if (game(player).isTransitioning()) return;
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SpectatorMainMenuHolder holder) {
            event.setCancelled(true);
            if (!player.equals(holder.viewer.player()) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
            int slot = event.getRawSlot();
            if (slot < holder.teams.size()) {
                openTeamSpectatorMenu(holder.viewer, holder.teams.get(slot));
                return;
            }
            int playerIndex = slot - holder.teams.size();
            if (playerIndex >= 0 && playerIndex < holder.players.size()) spectateTarget(holder.viewer, holder.players.get(playerIndex));
            return;
        }
        if (top.getHolder() instanceof SpectatorTeamMenuHolder holder) {
            event.setCancelled(true);
            if (!player.equals(holder.viewer.player()) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
            if (event.getRawSlot() == holder.backSlot) {
                openMainSpectatorMenu(holder.viewer);
                return;
            }
            int index = 0;
            for (int slot = 0; slot < top.getSize(); slot++) {
                if (slot == holder.backSlot) continue;
                if (index >= holder.players.size()) break;
                if (slot == event.getRawSlot()) {
                    spectateTarget(holder.viewer, holder.players.get(index));
                    return;
                }
                index++;
            }
            return;
        }
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) return;
        if (gamePlayer.isSpectator()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SpectatorMainMenuHolder || top.getHolder() instanceof SpectatorTeamMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) return;
        if (gamePlayer.isSpectator()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    @EventHandler
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null) return;
        if (gamePlayer.isSpectator()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) return;
        if (gamePlayer.isSpectator()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
            return;
        }
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) return;
        if (gamePlayer.isSpectator() || GamePlayer.isSpectatorCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
        }
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) return;
        Bukkit.getScheduler().runTask(moduleService.plugin(), () -> syncInventory(gamePlayer));
    }

    private void openMainSpectatorMenu(GamePlayer viewer) {
        if (!viewer.isSpectator() || viewer.player() == null) return;
        List<GameTeam> teams = new ArrayList<>();
        for (GameTeam team : viewer.spectatableTeams()) if (hasVisibleTargets(team)) teams.add(team);
        List<GamePlayer> players = new ArrayList<>();
        for (GamePlayer player : viewer.spectatablePlayers()) if (isVisibleTarget(player)) players.add(player);
        SpectatorMainMenuHolder holder = new SpectatorMainMenuHolder(viewer, teams, players, menuSize(teams.size() + players.size(), 1));
        int slot = 0;
        for (GameTeam team : teams) holder.inventory.setItem(slot++, teamItem(team, viewer.team() == team));
        for (GamePlayer player : players) holder.inventory.setItem(slot++, playerItem(player));
        viewer.player().openInventory(holder.inventory);
    }

    private void openTeamSpectatorMenu(GamePlayer viewer, GameTeam team) {
        if (!viewer.isSpectator() || viewer.player() == null) return;
        List<GamePlayer> players = new ArrayList<>();
        for (GamePlayer player : team.getMembers()) if (isVisibleTarget(player)) players.add(player);
        SpectatorTeamMenuHolder holder = new SpectatorTeamMenuHolder(viewer, players, submenuSize(players.size()));
        int index = 0;
        for (int slot = 0; slot < holder.inventory.getSize() && index < players.size(); slot++) {
            if (slot == holder.backSlot) continue;
            holder.inventory.setItem(slot, playerItem(players.get(index++)));
        }
        holder.inventory.setItem(holder.backSlot, backItem());
        viewer.player().openInventory(holder.inventory);
    }

    private void spectateTarget(GamePlayer viewer, GamePlayer target) {
        if (viewer.player() == null) return;
        if (!viewer.isSpectator() || !isVisibleTarget(target)) return;
        viewer.teleport(target.location());
        viewer.player().closeInventory();
    }

    private boolean hasVisibleTargets(GameTeam team) {
        for (GamePlayer player : team.getMembers()) if (isVisibleTarget(player)) return true;
        return false;
    }

    private boolean isVisibleTarget(GamePlayer player) {
        return player != null && player.isOnline() && !player.isSpectator();
    }

    private ItemStack teamItem(GameTeam team, boolean selected) {
        Material type = itemService.mappedDyeMaterial(team.color());
        ItemStack item = new ItemStack(type == null ? Material.WHITE_DYE : type);
        item.editMeta(meta -> {
            meta.itemName(team.displayName());
            if (selected) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private ItemStack playerItem(GamePlayer player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(Bukkit.createProfile(player.uuid(), player.name()));
            meta.itemName(Component.text(player.name(), NamedTextColor.WHITE));
        });
        return item;
    }

    private ItemStack backItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> meta.itemName(Component.text("Back", NamedTextColor.RED)));
        return item;
    }

    private int menuSize(int entries, int minimumRows) {
        int rows = Math.max(minimumRows, (entries + 8) / 9);
        return Math.max(9, Math.min(54, rows * 9));
    }

    private int submenuSize(int playerCount) {
        int rows = Math.max(2, (playerCount + 1 + 8) / 9);
        return Math.max(18, Math.min(54, rows * 9));
    }

    private void syncInventory(GamePlayer player) {
        itemService.normalizeInventory(player);
        if (player.player() == null) return;
        if (player.isSpectator()) player.syncSpectatorState();
        else GamePlayer.removeSpectatorCompass(player.player().getInventory());
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
                if (!crystal.isValid()) return;
                crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, false, true, player);
                crystal.remove();
            }, fuse);
        }
    }

    private EntityType spawnType(Material material) {
        String name = material.name();
        if (!name.endsWith("_SPAWN_EGG")) return "TNT".equals(name) ? EntityType.TNT : null;
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

    private GameModule game(Player player) {
        return moduleService.getGameOfPlayer(player);
    }

    private record PendingAutoIgnite(UUID playerId, EntityType entityType, int fuse) {}

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class SpectatorMainMenuHolder implements InventoryHolder {
        private final GamePlayer viewer;
        private final List<GameTeam> teams;
        private final List<GamePlayer> players;
        private final Inventory inventory;

        private SpectatorMainMenuHolder(GamePlayer viewer, List<GameTeam> teams, List<GamePlayer> players, int size) {
            this.viewer = viewer;
            this.teams = List.copyOf(teams);
            this.players = List.copyOf(players);
            this.inventory = Bukkit.createInventory(this, size, Component.text("Spectate"));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SpectatorTeamMenuHolder implements InventoryHolder {
        private final GamePlayer viewer;
        private final List<GamePlayer> players;
        private final Inventory inventory;
        private final int backSlot;

        private SpectatorTeamMenuHolder(GamePlayer viewer, List<GamePlayer> players, int size) {
            this.viewer = viewer;
            this.players = List.copyOf(players);
            this.inventory = Bukkit.createInventory(this, size, Component.text("Spectate Team"));
            this.backSlot = size - 9;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
