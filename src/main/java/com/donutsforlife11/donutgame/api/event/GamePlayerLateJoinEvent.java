package com.donutsforlife11.donutgame.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerLateJoinEvent extends PlayerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final GameModule module;
    private final GamePlayer gamePlayer;
    private boolean joinAsSpectator = true;
    private GameLocation spectatorLocation;

    public GamePlayerLateJoinEvent(@NotNull Player player, GameModule module, GamePlayer gamePlayer, GameLocation spectatorLocation) {
        super(player);
        this.module = module;
        this.gamePlayer = gamePlayer;
        this.spectatorLocation = spectatorLocation;
    }

    public GameModule module() {
        return module;
    }

    public GamePlayer gamePlayer() {
        return gamePlayer;
    }

    public boolean joinAsSpectator() {
        return joinAsSpectator;
    }

    public void setJoinAsSpectator(boolean joinAsSpectator) {
        this.joinAsSpectator = joinAsSpectator;
    }

    public GameLocation spectatorLocation() {
        return spectatorLocation;
    }

    public void setSpectatorLocation(GameLocation spectatorLocation) {
        this.spectatorLocation = spectatorLocation;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
