package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public final class GamePlayerLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameModule module;
    private final GamePlayer player;

    public GamePlayerLeaveEvent(GameModule module, GamePlayer player) { this.module = module; this.player = player; }
    public GameModule module() { return module; }
    public GamePlayer gamePlayer() { return player; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
