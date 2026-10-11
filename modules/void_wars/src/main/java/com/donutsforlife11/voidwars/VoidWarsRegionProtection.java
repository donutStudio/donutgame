package com.donutsforlife11.voidwars;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

final class VoidWarsRegionProtection {
    private final VoidWars game;

    VoidWarsRegionProtection(VoidWars game) {
        this.game = game;
    }

    @GameEventHandler
    public void onMove(GameEvent<PlayerMoveEvent> event) {
        GamePlayer player = event.getPlayer();
        GameLocation to = event.getTo();
        if (player == null || to == null || player.isSpectator() || game.roundStarted() || game.world().regionContainsPos(VoidWars.BORDER, to)) {
            return;
        }
        event.setTo(game.world().getPoint(VoidWars.SPAWN));
    }

    @GameEventHandler
    public void onBlockBreak(GameEvent<BlockBreakEvent> event) {
        if (protectedLocation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        if (protectedLocation(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @GameEventHandler
    public void onEntityExplode(GameEvent<EntityExplodeEvent> event) {
        event.removeBlocksIf(this::protectedLocation);
    }

    @GameEventHandler
    public void onBlockExplode(GameEvent<BlockExplodeEvent> event) {
        event.removeBlocksIf(this::protectedLocation);
    }

    private boolean protectedLocation(GameBlock block) {
        return block != null && protectedLocation(block.location());
    }

    private boolean protectedLocation(GameLocation location) {
        if (!game.roundStarted() && !game.roundEnding()) {
            return true;
        }
        return game.protectRegions && !game.world().regionContainsPos(VoidWars.MUTABLE, location);
    }
}
