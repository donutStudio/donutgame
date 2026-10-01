package com.donutsforlife11.voidwars;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

final class VoidWarsRegionProtection {
    private final VoidWars game;

    VoidWarsRegionProtection(VoidWars game) {
        this.game = game;
    }

    @GameEventHandler
    public void onMove(GameEvent<PlayerMoveEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        GameLocation to = event.get("to", GameLocation.class);
        if (player == null || to == null || player.isSpectator() || game.roundStarted() || game.world().regionContainsPos(VoidWars.BORDER, to)) {
            return;
        }
        event.bukkitEvent().setTo(game.world().getPoint(VoidWars.SPAWN).bukkitLocation());
    }

    @GameEventHandler
    public void onBlockBreak(GameEvent<BlockBreakEvent> event) {
        if (protectedLocation(event.get("block", Block.class))) {
            event.bukkitEvent().setCancelled(true);
        }
    }

    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        if (protectedLocation(event.get("block", Block.class))) {
            event.bukkitEvent().setCancelled(true);
        }
    }

    @GameEventHandler
    public void onEntityExplode(GameEvent<EntityExplodeEvent> event) {
        event.bukkitEvent().blockList().removeIf(this::protectedLocation);
    }

    @GameEventHandler
    public void onBlockExplode(GameEvent<BlockExplodeEvent> event) {
        event.bukkitEvent().blockList().removeIf(this::protectedLocation);
    }

    private boolean protectedLocation(Block block) {
        return block != null && protectedLocation(new GameLocation(game.world(), block.getLocation()));
    }

    private boolean protectedLocation(GameLocation location) {
        if (!game.roundStarted() && !game.roundEnding()) {
            return true;
        }
        return game.protectRegions && !game.world().regionContainsPos(VoidWars.MUTABLE, location);
    }
}
