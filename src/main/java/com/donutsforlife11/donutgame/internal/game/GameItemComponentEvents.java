package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameItemComponentEvents implements Listener {
    private final ModuleService moduleService;

    public GameItemComponentEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler
    public void handleDummyComponent(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isUseAction(event.getAction())) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }
        GameItem item = GameItem.from(stack);
        if (item == null || !item.hasData(GameItemComponents.DUMMY)) {
            return;
        }

        String value = item.getDataOrDefault(GameItemComponents.DUMMY, "placeholder");
        GameModule module = moduleService.getGameOfPlayer(event.getPlayer());
        GamePlayer player = module == null ? null : module.playerManager().getPlayer(event.getPlayer());
        Component message = Component.text("Dummy item component: " + value, NamedTextColor.LIGHT_PURPLE);
        if (player != null) {
            player.actionbar(message);
            player.playSound(Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.8f, 1.6f);
        } else {
            event.getPlayer().sendActionBar(message);
            event.getPlayer().playSound(event.getPlayer(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.8f, 1.6f);
        }
    }

    private boolean isUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
