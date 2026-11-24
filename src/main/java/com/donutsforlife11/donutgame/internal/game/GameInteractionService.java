package com.donutsforlife11.donutgame.internal.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.item.*;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;

public final class GameInteractionService implements Listener {
    private final GameModule module;
    private final Map<String, GameItemInteraction> actions = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private GameTimer timer;

    public GameInteractionService(GameModule module) {
        this.module = module;
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getPlugin(Donutgame.class));
    }

    public void register(String id, GameItemInteraction action) { actions.put(id, action); }

    @EventHandler(priority = EventPriority.HIGH)
    public void press(PlayerInteractEvent event) {
        if (!module.acceptsLiveOperations() || event.getHand() == null || event.getItem() == null
            || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        GamePlayer player = module.playerManager().getPlayer(event.getPlayer().getUniqueId());
        if (player == null || player.isSpectator() || event.getPlayer().getWorld() != com.donutsforlife11.donutgame.api.map.GameWorldHandle.bukkitWorld(module.world())) return;
        GameItem item = GameItem.from(event.getItem());
        String id = item.getData(GameItemComponents.INTERACT_EVENT);
        GameItemInteraction action = actions.get(id);
        if (action == null || active.containsKey(player.uuid())) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
        Active use = new Active(player, event.getPlayer(), item, event.getHand(), action);
        active.put(player.uuid(), use);
        if (action.press() != null) action.press().accept(use.context());
        if (timer == null) timer = module.timeManager().newTimer().onTick(1, ignored -> tick()).start();
    }

    @EventHandler public void stop(PlayerStopUsingItemEvent event) { release(event.getPlayer().getUniqueId()); }

    private void tick() {
        // RODO: Only players currently holding a registered item are checked; stop the timer when the last use ends.
        for (Active use : java.util.List.copyOf(active.values())) {
            use.ticks++;
            GameItem current = use.player.getItem(use.hand);
            if (!use.player.isOnline() || use.player.isSpectator() || current == null || !current.isSimilar(use.item)
                || (use.ticks > 1 && !use.online.isHandRaised())) {
                release(use.player.uuid());
            } else if (use.action.hold() != null) use.action.hold().accept(use.context());
        }
        if (active.isEmpty() && timer != null) { timer.cancel(); timer = null; }
    }

    private void release(UUID uuid) {
        Active use = active.remove(uuid);
        if (use != null && module.acceptsLiveOperations() && use.action.release() != null) use.action.release().accept(use.context());
    }

    public void close() {
        if (timer != null) timer.cancel();
        active.clear();
        actions.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class Active {
        final GamePlayer player;
        final Player online;
        final GameItem item;
        final EquipmentSlot hand;
        final GameItemInteraction action;
        int ticks;
        Active(GamePlayer player, Player online, GameItem item, EquipmentSlot hand, GameItemInteraction action) {
            this.player = player; this.online = online; this.item = item; this.hand = hand; this.action = action;
        }
        GameItemInteraction.Use context() { return new GameItemInteraction.Use(player, item, hand, ticks); }
    }
}
