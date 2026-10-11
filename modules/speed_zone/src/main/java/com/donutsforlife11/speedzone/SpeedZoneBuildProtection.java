package com.donutsforlife11.speedzone;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

final class SpeedZoneBuildProtection {
    private final Set<BlockKey> playerPlacedBlocks = new HashSet<>();
    private final SpeedZone game;

    SpeedZoneBuildProtection(SpeedZone game) {
        this.game = game;
    }

    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        GamePlayer player = event.getPlayer();
        GameBlock block = event.getBlockPlaced();
        if (player != null && player.gameMode() == GameMode.SURVIVAL) {
            playerPlacedBlocks.add(BlockKey.of(block));
        }
    }

    @GameEventHandler
    public void onBlockBreak(GameEvent<BlockBreakEvent> event) {
        GamePlayer player = event.getPlayer();
        GameBlock block = event.getBlock();
        if (player == null || player.gameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!playerPlacedBlocks.remove(BlockKey.of(block))) {
            event.setCancelled(true);
        }
    }

    @GameEventHandler
    public void onMove(GameEvent<PlayerMoveEvent> event) {
        if (game.hasStarted()) {
            return;
        }
        GamePlayer player = event.getPlayer();
        GameLocation to = event.getTo();
        if (player == null || to == null || player.isSpectator() || game.world().getRegions(SpeedZone.SPAWN_AREA).isEmpty()) {
            return;
        }
        if (!game.world().regionContainsPos(SpeedZone.SPAWN_AREA, to)) {
            event.setTo(game.checkpoints().get(0));
        }
    }

    private record BlockKey(String worldId, int x, int y, int z) {
        private static BlockKey of(GameBlock block) {
            if (block == null) {
                return null;
            }
            GameLocation location = block.location();
            return new BlockKey(block.world().id(), location.blockX(), location.blockY(), location.blockZ());
        }
    }
}
