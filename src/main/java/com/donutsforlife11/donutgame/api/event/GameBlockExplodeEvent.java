package com.donutsforlife11.donutgame.api.event;

import java.util.AbstractList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockExplodeEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameBlockExplodeEvent extends GameBlockEvent<BlockExplodeEvent> {
    GameBlockExplodeEvent(BlockExplodeEvent event, GameLocation location, GameModule module) {
        super(event, location, module);
    }

    public List<GameLocation> blockList() {
        return scopedBlockList(event().blockList());
    }

    private List<GameLocation> scopedBlockList(List<Block> blocks) {
        return new AbstractList<>() {
            @Override
            public GameLocation get(int index) {
                return gameBlock(blocks.get(backingIndex(index)));
            }

            @Override
            public int size() {
                int size = 0;
                for (Block block : blocks) if (gameBlock(block) != null) size++;
                return size;
            }

            @Override
            public GameLocation remove(int index) {
                Block removed = blocks.remove(backingIndex(index));
                return gameBlock(removed);
            }

            private int backingIndex(int visibleIndex) {
                int currentVisibleIndex = 0;
                for (int backingIndex = 0; backingIndex < blocks.size(); backingIndex++) {
                    if (gameBlock(blocks.get(backingIndex)) == null) continue;
                    if (currentVisibleIndex == visibleIndex) return backingIndex;
                    currentVisibleIndex++;
                }
                throw new IndexOutOfBoundsException(visibleIndex);
            }
        };
    }
}
