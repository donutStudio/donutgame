package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PlayerManager {
    public Collection<Player> getPlayers();

    public void setSpectator(Player player);

    public Collection<Player> getSpectators();

    public Collection<Player> getNonSpectators();

    public void setPlayerSpawn(Player player, Location location);

    public Location getPlayerSpawn(Player player);

    public void respawnPlayer(Player player);

    public void respawnPlayer(Player player, int time);

    public void cancelRespawn(Player player);

    public PlayerManager onPlayerRegistered(Consumer<Player> action);

    public PlayerManager onPlayerDeath(Consumer<Player> action);

    public PlayerManager onPlayerDisconnect(Consumer<Player> action);

    public PlayerManager onPlayerKill(BiConsumer<Player, Player> action);
}
