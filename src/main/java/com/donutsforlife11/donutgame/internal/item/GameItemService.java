package com.donutsforlife11.donutgame.internal.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.donutsforlife11.donutgame.api.data.GameItemAttributes;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.ModuleService;

public class GameItemService {
    private final ModuleService moduleService;

    public GameItemService(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    public void give(GamePlayer player, Collection<ItemStack> items) {
        Player bukkitPlayer = player == null ? null : player.player();
        if (player == null || items == null || items.isEmpty()) return;
        List<ItemStack> normalizedItems = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            ItemStack normalized = normalizeForInventory(player, item == null ? null : item.clone());
            if (normalized != null && normalized.getType() != Material.AIR) normalizedItems.add(normalized);
        }
        Map<Integer, ItemStack> leftovers = player.addToStoredInventory(normalizedItems);
        if (bukkitPlayer == null) return;
        for (ItemStack leftover : leftovers.values()) {
            bukkitPlayer.getWorld().dropItemNaturally(bukkitPlayer.getLocation(), leftover);
        }
    }

    public void normalizeInventory(GamePlayer player) {
        Player bukkitPlayer = player == null ? null : player.player();
        if (bukkitPlayer == null || player.isSpectator()) return;
        PlayerInventory inventory = bukkitPlayer.getInventory();
        boolean changed = false;
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack normalized = normalizeForInventory(player, storage[i]);
            if (!similarStack(storage[i], normalized)) {
                storage[i] = normalized;
                changed = true;
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack normalized = normalizeForInventory(player, armor[i]);
            if (!similarStack(armor[i], normalized)) {
                armor[i] = normalized;
                changed = true;
            }
        }
        ItemStack offhand = normalizeForInventory(player, inventory.getItemInOffHand());
        if (!similarStack(inventory.getItemInOffHand(), offhand)) {
            inventory.setItemInOffHand(offhand);
            changed = true;
        }
        if (changed) {
            inventory.setStorageContents(storage);
            inventory.setArmorContents(armor);
            player.captureLiveState();
            bukkitPlayer.updateInventory();
        }
    }

    public void replenishPlacedBlock(GamePlayer player, ItemStack placedItem, EquipmentSlot hand) {
        Player bukkitPlayer = player == null ? null : player.player();
        if (bukkitPlayer == null || !hasInfiniteBuild(placedItem)) return;
        PlayerInventory inventory = bukkitPlayer.getInventory();
        ItemStack current = hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        ItemStack replacement = current == null || current.getType() == Material.AIR ? placedItem.clone() : current.clone();
        replacement = normalizeForInventory(player, replacement);
        if (replacement == null || replacement.getType() == Material.AIR) return;
        replacement.setAmount(Math.max(1, Math.min(placedItem.getAmount(), replacement.getMaxStackSize())));
        if (hand == EquipmentSlot.HAND) inventory.setItemInMainHand(replacement);
        else inventory.setItemInOffHand(replacement);
        player.captureLiveState();
    }

    public boolean hasInfiniteBuild(ItemStack item) {
        return GameItemAttributes.hasInfiniteBuild(item);
    }

    public int autoIgniteFuse(ItemStack item) {
        return GameItemAttributes.autoIgniteFuse(item);
    }

    public ModuleService moduleService() {
        return moduleService;
    }

    private ItemStack normalizeForInventory(GamePlayer player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        return GameItemAttributes.normalize(GameItemAttributes.teamSyncedItem(player, item.clone()));
    }

    private boolean similarStack(ItemStack left, ItemStack right) {
        if (left == null || left.getType() == Material.AIR) return right == null || right.getType() == Material.AIR;
        if (right == null || right.getType() == Material.AIR) return false;
        return left.equals(right);
    }
}
