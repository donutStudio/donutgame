package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class PlayerManager {
    private final GameModule module;

    private final Map<Player, Boolean> players = new HashMap<>();
    private final Map<Player, Location> playerSpawnPoints = new HashMap<>();
    private final List<Consumer<Player>> registrationActions = new ArrayList<>();

    public PlayerManager(GameModule module) {
        this.module = module;
    }

    public void setSpectator(Player player) {
        players.replace(player, true);
    }
    public void setNonSpectator(Player player) {
        players.replace(player, false);
    }
    public boolean isSpectator(Player player) {
        return players.get(player).booleanValue();
    }
    public void setPlayerSpawn(Player player, Location location) {
        playerSpawnPoints.replace(player, location);
        player.setRespawnLocation(location, true);
    }
    public PlayerManager onPlayerRegistered(Consumer<Player> action) {
        Consumer<Player> checkedAction = Objects.requireNonNull(action);
        registrationActions.add(checkedAction);
        return this;
    }

    public Collection<Player> getPlayers() {
        return Collections.unmodifiableSet(players.keySet());
    }
    public Collection<Player> getSpectators() {
        Set<Player> spectators = new HashSet<>();
        for (Player player : players.keySet()) {
            if (players.get(player).booleanValue()) {
                spectators.add(player);
            }
        }
        return spectators;
    }
    public Collection<Player> getNonSpectators() {
        Set<Player> nonSpectators = new HashSet<>();
        for (Player player : players.keySet()) {
            if (!players.get(player).booleanValue()) {
                nonSpectators.add(player);
            }
        }
        return nonSpectators;
    }
    public Location getPlayerSpawn(Player player) {
        return playerSpawnPoints.get(player);
    }

    void registerPlayer(Player player) {
        Objects.requireNonNull(player);
        players.put(player, false);
        playerSpawnPoints.put(player, module.mapManager().currentWorld().getBukkitWorld().getSpawnLocation());
        player.teleportAsync(playerSpawnPoints.get(player));
    }
    void unregisterPlayer(Player player) {
        Objects.requireNonNull(player);
        players.remove(player);
        playerSpawnPoints.remove(player);
    }
}
