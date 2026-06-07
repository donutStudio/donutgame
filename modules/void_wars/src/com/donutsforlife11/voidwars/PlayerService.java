package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

import net.kyori.adventure.text.Component;

public class PlayerService {
    private VoidWars game;
    private PlayerManager playerManager;

    public PlayerService(VoidWars game, PlayerManager playerManager) {
        this.game = game;
        this.playerManager = playerManager;
    }

    public void playerEvents() {
        playerManager.onPlayerRegistered(player -> {
            player.teleportAsync(this.game.getMap().getPoints("spawn").get(0));
            if (this.game.gameStarted()) {
                playerManager.setSpectator(player);
                game.getContext().getLogger().info("sahur the goat");
            }
            game.uiManager().chat(player, Component.text("tung tung sahur"));
            game.uiManager().subtitle(player, Component.text("monkey corn"));
        });
    }
}
