package com.donutsforlife11.donutgame.api.player;

import org.bukkit.World;
import org.bukkit.entity.Player;

final class GamePlayerLifecycleService {
    void attach(GamePlayer gamePlayer, Player player, boolean freshSession) {
        if (gamePlayer == null || player == null) {
            return;
        }
        gamePlayer.captureExternalScoreboard(player);
        if (freshSession) {
            gamePlayer.startFreshSession(player);
        } else {
            gamePlayer.remember(player);
        }
    }

    void markOffline(GamePlayer gamePlayer, Player player) {
        if (gamePlayer == null || player == null) {
            return;
        }
        gamePlayer.remember(player);
        gamePlayer.markOffline();
    }

    void prepareForRemoval(GamePlayer gamePlayer) {
        if (gamePlayer != null) {
            gamePlayer.prepareForRemoval();
        }
    }

    void prepareForWorldExit(GamePlayer gamePlayer) {
        if (gamePlayer != null) {
            gamePlayer.prepareForWorldExit();
        }
    }

    void clearSpectatorStateAfterWorldExit(GamePlayer gamePlayer, Player player) {
        if (gamePlayer != null) {
            gamePlayer.clearSpectatorStateAfterWorldExit(player);
        }
    }

    boolean playerStillInOwnedWorld(Player player, java.util.function.Predicate<World> ownsWorld) {
        return player != null && ownsWorld != null && ownsWorld.test(player.getWorld());
    }
}
