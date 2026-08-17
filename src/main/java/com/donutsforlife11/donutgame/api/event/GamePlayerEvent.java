package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerEvent<T extends Event> extends GameEvent<T> {
    private final GamePlayer player;

    GamePlayerEvent(T event, GamePlayer player) {
        this(event, player, null);
    }

    GamePlayerEvent(T event, GamePlayer player, GameModule module) {
        super(event, module);
        this.player = player;
    }

    public GamePlayer getPlayer() {
        return player;
    }
}
