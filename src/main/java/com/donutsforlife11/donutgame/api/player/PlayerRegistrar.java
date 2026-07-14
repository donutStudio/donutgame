package com.donutsforlife11.donutgame.api.player;

import org.bukkit.entity.Player;

public class PlayerRegistrar {
    public static void registerPlayer(PlayerManager playerManager, Player player) {
        playerManager.registerPlayer(player);
    }
    public static void unregisterPlayer(PlayerManager playerManager, Player player) {
        playerManager.unregisterPlayer(player);
    }
}
