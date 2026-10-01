package com.donutsforlife11.donutgame.api.player;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.item.GameItemComponents;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Runtime-only spectator state.
 *
 * This class deliberately does not persist logical spectator state. The
 * GamePlayer instance is the sole authority for whether the player is currently
 * spectating; this object only projects that in-memory state onto an attached
 * Bukkit Player while the player is online.
 */
final class SpectatorSession implements AutoCloseable {
    private static final int MENU_SLOT = 0;

    private final GamePlayer owner;
    private boolean closed;

    private SpectatorSession(GamePlayer owner) {
        this.owner = owner;
    }

    static SpectatorSession open(GamePlayer owner, Player player) {
        SpectatorSession session = new SpectatorSession(owner);
        session.apply(player);
        return session;
    }

    @Override
    public void close() {
        close(owner.bukkitPlayer());
    }

    void close(Player player) {
        if (closed) {
            return;
        }
        closed = true;
        detach(player);
    }

    /**
     * Removes spectator-only visibility/list presentation from an attached
     * Bukkit player without changing the logical spectator session. This is
     * used on disconnect while GamePlayer restores the normal Bukkit
     * projection without clearing the in-memory spectator session.
     */
    void detach(Player player) {
        if (player == null) {
            return;
        }
        owner.module().plugin().spectatorService().hideSpectator(owner);
        removeMenuItem(player);
        owner.module().log("[spectator-debug] Cleared spectator visibility/list projection for " + player.getName() + " (" + owner.uuid() + ").");
    }

    void apply(Player player) {
        if (closed || player == null || !owner.isOnline() || !owner.uuid().equals(player.getUniqueId())) {
            return;
        }
        // Spectator restrictions are authoritative in memory and enforced by
        // SpectatorGuardEvents/SpectatorService. Do not set Bukkit invulnerability,
        // invisibility, pickup, or game-mode flags that can be serialized to player.dat.
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
        player.getInventory().setItem(MENU_SLOT, menuItem());
        player.updateInventory();
        owner.module().plugin().spectatorService().showSpectator(owner);
        owner.module().log(
            "[spectator-debug] Applied spectator projection for " + player.getName() + " (" + owner.uuid() + ")"
                + "; allowFlight=" + player.getAllowFlight() + "; flying=" + player.isFlying()
                + "; invulnerable=" + player.isInvulnerable() + "; invisible=" + player.isInvisible()
        );
    }

    private void removeMenuItem(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (GameItemComponents.isSpectatorMenu(item)) {
                player.getInventory().setItem(slot, null);
            }
        }
        player.updateInventory();
    }

    private ItemStack menuItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Spectator Menu", NamedTextColor.AQUA));
        item.editPersistentDataContainer(pdc ->
            pdc.set(GameItemComponents.SPECTATOR_MENU.key(), GameItemComponents.SPECTATOR_MENU.type(), "menu")
        );
        return item;
    }
}
