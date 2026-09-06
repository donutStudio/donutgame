package com.donutsforlife11.donutgame.api.player;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayer {
    private final PlayerManager playerManager;
    private final UUID uuid;
    private PlayerState state;
    private String lastWorldName;
    private Location lastLocation;

    GamePlayer(PlayerManager playerManager, UUID uuid) {
        this.playerManager = playerManager;
        this.uuid = uuid;
        this.state = PlayerState.OFFLINE;
    }

    public UUID uuid() {
        return uuid;
    }

    public Player player() {
        return Bukkit.getPlayer(uuid);
    }

    public Player bukkitPlayer() {
        return player();
    }

    public GameModule module() {
        return playerManager.module();
    }

    public PlayerState state() {
        return state;
    }

    public boolean isOnline() {
        return state == PlayerState.ONLINE && player() != null;
    }

    public String lastWorldName() {
        return lastWorldName;
    }

    public Location lastLocation() {
        return lastLocation == null ? null : lastLocation.clone();
    }

    void remember(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        state = PlayerState.ONLINE;
        World world = player.getWorld();
        lastWorldName = world == null ? null : world.getName();
        lastLocation = player.getLocation().clone();
    }

    void markOffline() {
        state = PlayerState.OFFLINE;
    }
}
