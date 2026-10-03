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
import org.bukkit.event.player.PlayerMoveEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
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

    @GameEventHandler
    public void onMove(GameEvent<PlayerMoveEvent> event) {
        if (game.hasStarted()) {
            return;
        }
        GamePlayer player = event.get("player", GamePlayer.class);
        GameLocation to = event.get("to", GameLocation.class);
        if (player == null || to == null || player.isSpectator() || game.world().getRegions(SpeedZone.SPAWN_AREA).isEmpty()) {
            return;
        }
        if (!game.world().regionContainsPos(SpeedZone.SPAWN_AREA, to)) {
            event.bukkitEvent().setTo(game.checkpoints().get(0).bukkitLocation());
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
