package com.donutsforlife11.donutgame.api.player;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.time.GameTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GamePlayer extends GameEntity {
    private final PlayerManager playerManager;

    private boolean spectator;
    private GameLocation respawnLocation;
    private GameTimer respawnTimer;

    GamePlayer(PlayerManager playerManager, GameWorld world, UUID uuid, GameLocation respawnLocation) {
        super(world, uuid, EntityType.PLAYER);
        this.playerManager = playerManager;
        this.respawnLocation = respawnLocation;
    }

    @Override
    public Player bukkitEntity() {
        return (Player) super.bukkitEntity();
    }

    public Player player() {
        return bukkitEntity();
    }

    public void setSpectator() {
        Player player = requirePlayer();
        spectator = true;
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void setNonSpectator() {
        Player player = requirePlayer();
        spectator = false;
        player.setGameMode(GameMode.SURVIVAL);
    }

    public void respawn() {
        respawn(0);
    }

    public void respawn(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative.");
        }
        cancelRespawn();
        setSpectator();
        respawnTimer = playerManager.module().timeManager().newTimer(ticks).onTick(20, timer -> {
            int remainingSeconds = Math.round(timer.getRemainingTicks() / 20.0f);
            String formattedTimeString = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            playerManager.module().uiManager().actionbar(
                this,
                Component.text("Respawning in: ")
                    .append(Component.text(formattedTimeString, NamedTextColor.GREEN))
            );
        }).onFinish(ignored -> {
            if (playerManager.isRegistered(this)) {
                teleport(respawnLocation);
                setNonSpectator();
            }
            respawnTimer = null;
        }).start();
    }

    public void cancelRespawn() {
        if (respawnTimer != null) {
            respawnTimer.cancel();
            respawnTimer = null;
        }
    }

    public void setRespawnLocation(GameLocation location) {
        respawnLocation = location;
        Player player = player();
        if (player != null) {
            player.setRespawnLocation(location.toBukkit(world().bukkitWorld()), true);
        }
    }

    public boolean isSpectator() {
        return spectator;
    }

    public boolean isOnline() {
        return player() != null;
    }

    public GameLocation respawnLocation() {
        return respawnLocation;
    }

    public GameTimer respawnTimer() {
        return respawnTimer;
    }

    private Player requirePlayer() {
        Player player = player();
        if (player == null) {
            throw new IllegalStateException("Player is not online in the game world.");
        }
        return player;
    }
}
