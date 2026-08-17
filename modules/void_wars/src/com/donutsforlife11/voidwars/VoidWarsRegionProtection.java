package com.donutsforlife11.voidwars;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class VoidWarsRegionProtection {
    private final VoidWars game;

    public VoidWarsRegionProtection(VoidWars game) {
        this.game = game;
    }

    @GameEventHandler
    public void onExitStartingBorder(GameEvent<PlayerMoveEvent> event) {
        GamePlayer player = event.getPlayer();
        if (player == null) {
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
    public void onBlockBroken(GameEvent<BlockBreakEvent> event) {
        if (actionDeniable(event.getLocation())) {
            event.setCancelled(true);
        }
    }
    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        if (actionDeniable(event.getLocation())) {
            event.setCancelled(true);
        }
    }
    @GameEventHandler
    public void onEntityExplode(GameEvent<EntityExplodeEvent> event) {
        event.blockList().removeIf(location -> actionDeniable(location));
    }
    @GameEventHandler
    public void onBlockExplode(GameEvent<BlockExplodeEvent> event) {
        event.blockList().removeIf(location -> actionDeniable(location));
    }

    private boolean actionDeniable(GameLocation location) {
        if (!game.roundStarted()) {
            return true;
        }
        return game.protectRegions && !game.world().posInRegion(location, game.mutableRegionName);
    }
}
