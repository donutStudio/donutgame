package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.block.BlockBreakEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameBlockBreakEvent extends GameBlockEvent<BlockBreakEvent> {
    GameBlockBreakEvent(BlockBreakEvent event, GameLocation location, GameModule module) {
        super(event, location, module);
    }
}
