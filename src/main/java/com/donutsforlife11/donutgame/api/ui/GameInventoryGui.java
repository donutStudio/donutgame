package com.donutsforlife11.donutgame.api.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;
import net.kyori.adventure.text.Component;

public final class GameInventoryGui implements InventoryHolder, Listener {
    private final GameModule module;
    private final Inventory inventory;
    public record Click(boolean right, boolean shift) { }
    private final Map<Integer, BiConsumer<GamePlayer, Click>> actions = new HashMap<>();
    private boolean closed;
    private final Runnable onClose;

    GameInventoryGui(GameModule module, int rows, Component title, java.util.function.Consumer<GameInventoryGui> onClose) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest menus need 1 to 6 rows.");
        this.module = module;
        this.onClose = () -> onClose.accept(this);
        inventory = Bukkit.createInventory(this, rows * 9, title);
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getPlugin(Donutgame.class));
    }

    @Override public Inventory getInventory() { return inventory; }

    public GameInventoryGui setItem(int slot, GameItem item, BiConsumer<GamePlayer, Click> action) {
        GameScope.requireActive(module);
        if (closed) throw new IllegalStateException("Menu is closed.");
        inventory.setItem(slot, item == null ? null : item.copyBukkitItem());
        if (action == null) actions.remove(slot); else actions.put(slot, action);
        return this;
    }

    public void open(GamePlayer player) {
        GameScope.requirePlayer(module, player);
        if (closed) throw new IllegalStateException("Menu is closed.");
        var online = GamePlayerHandle.bukkitPlayer(player);
        if (online != null) online.openInventory(inventory);
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (var viewer : java.util.List.copyOf(inventory.getViewers())) viewer.closeInventory();
        actions.clear();
        HandlerList.unregisterAll(this);
        onClose.run();
    }

    void closeViewer(org.bukkit.entity.Player player) {
        if (inventory.getViewers().contains(player)) player.closeInventory();
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        // RODO: Cancel the whole view, including shift clicks and number-key transfers from the player's inventory.
        event.setCancelled(true);
        if (closed || !module.acceptsLiveOperations()) return;
        GamePlayer player = module.playerManager().getPlayer(event.getWhoClicked().getUniqueId());
        var action = actions.get(event.getRawSlot());
        if (player != null && action != null && (event.isLeftClick() || event.isRightClick())) {
            Click click = new Click(event.isRightClick(), event.isShiftClick());
            // RODO: Run after inventory dispatch so callbacks can safely close or replace a menu.
            module.timeManager().newTimer(1).onFinish(ignored -> {
                if (!closed && player.isOnline()) action.accept(player, click);
            }).start();
        }
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() == inventory) event.setCancelled(true);
    }
}
