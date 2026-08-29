package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.player.PlayerQuitEvent;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerQuitEvent extends GamePlayerEvent<PlayerQuitEvent> {
    GamePlayerQuitEvent(PlayerQuitEvent event, GamePlayer player, GameModule module) {
        super(event, player, module);
    }
}
