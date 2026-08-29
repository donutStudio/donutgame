package com.donutsforlife11.donutgame.api.event;

import org.bukkit.event.block.BlockPlaceEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameBlockPlaceEvent extends GameBlockEvent<BlockPlaceEvent> {
    GameBlockPlaceEvent(BlockPlaceEvent event, GameLocation location, GameModule module) {
        super(event, location, module);
    }
}
