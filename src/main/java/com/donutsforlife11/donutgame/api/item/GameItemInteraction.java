package com.donutsforlife11.donutgame.api.item;

import java.util.function.Consumer;
import org.bukkit.inventory.EquipmentSlot;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public record GameItemInteraction(Consumer<Use> press, Consumer<Use> hold, Consumer<Use> release) {
    public record Use(GamePlayer player, GameItem item, EquipmentSlot hand, int ticks) { }
}
