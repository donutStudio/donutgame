package com.donutsforlife11.speedzone;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;

final class SpeedZoneBuildProtection {
    private final Set<BlockKey> playerPlacedBlocks = new HashSet<>();

    @GameEventHandler
    public void onBlockPlace(GameEvent<BlockPlaceEvent> event) {
        Player player = event.bukkitEvent().getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            playerPlacedBlocks.add(BlockKey.of(event.bukkitEvent().getBlockPlaced()));
        }
    }

    @GameEventHandler
    public void onBlockBreak(GameEvent<BlockBreakEvent> event) {
        Player player = event.bukkitEvent().getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!playerPlacedBlocks.remove(BlockKey.of(event.bukkitEvent().getBlock()))) {
            event.bukkitEvent().setCancelled(true);
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
