package com.donutsforlife11.donutgame.api.player;

import org.bukkit.entity.Player;

public class PlayerRegistrar {
    public static GamePlayer registerPlayer(PlayerManager playerManager, Player player) {
        playerManager.register(player);
        return playerManager.getPlayer(player);
    }

    public static void unregisterPlayer(PlayerManager playerManager, Player player) {
        playerManager.unregister(player);
    }
}
