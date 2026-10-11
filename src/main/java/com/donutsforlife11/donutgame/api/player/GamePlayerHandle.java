package com.donutsforlife11.donutgame.api.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Internal bridge for framework code that must interoperate with Paper.
 * Game modules should use {@link GamePlayer}'s scoped API instead.
 */
public final class GamePlayerHandle {
    private GamePlayerHandle() {
    }

    public static Player bukkitPlayer(GamePlayer player) {
        return player == null ? null : player.bukkitPlayer();
    }

    public static Player requireBukkitPlayer(GamePlayer player) {
        Player bukkitPlayer = bukkitPlayer(player);
        if (bukkitPlayer == null) {
            throw new IllegalStateException("GamePlayer " + (player == null ? "<null>" : player.uuid()) + " is not online.");
        }
        return bukkitPlayer;
    }

    public static Entity bukkitEntity(GamePlayer player) {
        return player == null ? null : player.bukkitEntity();
    }

    public static void captureExternalScoreboard(GamePlayer player, Player bukkitPlayer) {
        if (player != null) {
            player.captureExternalScoreboard(bukkitPlayer);
        }
    }

    public static void restoreExternalScoreboard(GamePlayer player, Player bukkitPlayer) {
        if (player != null) {
            player.restoreExternalScoreboard(bukkitPlayer);
        }
    }
}
