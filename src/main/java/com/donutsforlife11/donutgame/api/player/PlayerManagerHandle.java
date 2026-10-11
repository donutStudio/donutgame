package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Internal bridge for framework code that must register or resolve Paper players.
 * Game modules should work with {@link GamePlayer} instances from scoped APIs.
 */
public final class PlayerManagerHandle {
    private PlayerManagerHandle() {
    }

    public static CompletableFuture<Integer> join(PlayerManager manager, Collection<Player> players) {
        return manager.join(players);
    }

    public static CompletableFuture<Integer> joinInitial(PlayerManager manager, Collection<Player> players) {
        return manager.joinInitial(players);
    }

    public static CompletableFuture<Boolean> join(PlayerManager manager, Player player, boolean lateJoin) {
        return manager.join(player, lateJoin);
    }

    public static CompletableFuture<Boolean> joinInitial(PlayerManager manager, Player player) {
        return manager.joinInitial(player);
    }

    public static CompletableFuture<Boolean> leave(PlayerManager manager, Player player) {
        return manager.leave(player);
    }

    public static boolean owns(PlayerManager manager, Player player) {
        return manager.owns(player);
    }

    public static boolean ownsWorld(PlayerManager manager, World world) {
        return manager.ownsWorld(world);
    }

    public static GamePlayer getPlayer(PlayerManager manager, Player player) {
        return manager.getPlayer(player);
    }

    public static void markOffline(PlayerManager manager, Player player) {
        manager.markOffline(player);
    }

    public static void detachAfterExternalWorldChange(PlayerManager manager, Player player) {
        manager.detachAfterExternalWorldChange(player);
    }
}
