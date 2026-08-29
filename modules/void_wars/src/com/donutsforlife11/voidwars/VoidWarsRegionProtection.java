package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.event.GameBlockBreakEvent;
import com.donutsforlife11.donutgame.api.event.GameBlockExplodeEvent;
import com.donutsforlife11.donutgame.api.event.GameBlockPlaceEvent;
import com.donutsforlife11.donutgame.api.event.GameEntityExplodeEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.event.GamePlayerMoveEvent;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class VoidWarsRegionProtection {
    private final VoidWars game;

    public VoidWarsRegionProtection(VoidWars game) {
        this.game = game;
    }

    @GameEventHandler
    public void onExitStartingBorder(GamePlayerMoveEvent event) {
        GamePlayer player = event.getPlayer();
        if (player.isSpectator()) {
            return;
        }
        GameLocation from = event.getFrom();
        GameLocation to = event.getTo();
        if (from == null || to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockZ() == to.getBlockZ()) {
            return; 
        }
        if (game.roundStarted()) {
            return;
        }
        if (!game.world().posInRegion(to, game.borderRegionName)) {
            event.setTo(game.world().getPoint(game.spawnPointName));
        }
    }

    @GameEventHandler
    public void onBlockBroken(GameBlockBreakEvent event) {
        if (actionDeniable(event.getLocation())) {
            event.setCancelled(true);
        }
    }
    @GameEventHandler
    public void onBlockPlace(GameBlockPlaceEvent event) {
        if (actionDeniable(event.getLocation())) {
            event.setCancelled(true);
        }
    }
    @GameEventHandler
    public void onEntityExplode(GameEntityExplodeEvent event) {
        event.blockList().removeIf(location -> actionDeniable(location));
    }
    @GameEventHandler
    public void onBlockExplode(GameBlockExplodeEvent event) {
        event.blockList().removeIf(location -> actionDeniable(location));
    }

    private boolean actionDeniable(GameLocation location) {
        if (!game.roundStarted() && !game.roundEnding()) {
            return true;
        }
        return game.protectRegions && !game.world().posInRegion(location, game.mutableRegionName);
    }
}
