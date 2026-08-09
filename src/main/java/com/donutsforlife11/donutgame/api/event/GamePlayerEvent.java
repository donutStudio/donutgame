package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.player.GamePlayer;

public record GamePlayerEvent<T extends Event>(
    T event,
    GamePlayer player
) {
}
