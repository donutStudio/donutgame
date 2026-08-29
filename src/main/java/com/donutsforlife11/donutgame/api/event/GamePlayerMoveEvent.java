package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerMoveEvent extends GamePlayerEvent<PlayerMoveEvent> {
    GamePlayerMoveEvent(PlayerMoveEvent event, GamePlayer player, GameModule module) {
        super(event, player, module);
    }

    public GameLocation getFrom() {
        return gameLocation(event().getFrom());
    }

    public GameLocation getTo() {
        return gameLocation(event().getTo());
    }

    public void setTo(GameLocation location) {
        if (location == null || module() == null || module().world() == null) return;
        event().setTo(location.toBukkit(module().world().bukkitWorld()));
    }
}
