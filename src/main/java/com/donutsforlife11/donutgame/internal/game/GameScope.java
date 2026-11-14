package com.donutsforlife11.donutgame.internal.game;

import java.util.Collection;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

/** Shared checks at the points where a module supplies a scoped object. */
public final class GameScope {
    private GameScope() {}

    public static void requireActive(GameModule module) {
        if (!module.acceptsLiveOperations()) {
            throw new IllegalStateException("Game module is no longer active.");
        }
    }

    public static void requireLocation(GameWorld world, GameLocation location) {
        if (location == null || (location.world() != null && location.world() != world)) {
            throw new IllegalArgumentException("Location must be in the owning game world.");
        }
        GameLocationHandle.requireCurrent(location);
    }

    public static void requirePlayer(GameModule module, GamePlayer player) {
        if (player.module() != module) {
            throw new IllegalArgumentException("Player belongs to a different game module.");
        }
        if (module.playerManager().getPlayer(player.uuid()) != player) {
            throw new IllegalStateException("Player is no longer registered with this game module.");
        }
    }

    public static Collection<GamePlayer> requirePlayers(GameModule module, Collection<GamePlayer> players) {
        if (players != null) {
            players.forEach(player -> requirePlayer(module, player));
        }
        return players;
    }
}
