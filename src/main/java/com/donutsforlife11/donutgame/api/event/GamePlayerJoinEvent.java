package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.player.PlayerJoinEvent;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerJoinEvent extends GamePlayerEvent<PlayerJoinEvent> {
    GamePlayerJoinEvent(PlayerJoinEvent event, GamePlayer player, GameModule module) {
        super(event, player, module);
    }
}
