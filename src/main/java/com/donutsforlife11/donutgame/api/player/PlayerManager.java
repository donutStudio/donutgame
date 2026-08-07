package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player; //

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerManager {
    private final GameModule module;

    private final List<Consumer<Player>> registrationActions = new ArrayList<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public PlayerManager(GameModule module) {
        this.module = module;
    }

    public void setSpectator(Player player) {
        // Stub spectator code
        player.setGameMode(GameMode.SPECTATOR);

        players.replace(player.getUniqueId(), new PlayerData(true, getPlayerSpawn(player), getRespawnTimer(player)));
    }
    public void setNonSpectator(Player player) {
        // Stub un-spectator code
        player.setGameMode(GameMode.SURVIVAL);

        players.replace(player.getUniqueId(), new PlayerData(false, getPlayerSpawn(player), getRespawnTimer(player)));
    }
    public boolean isSpectator(Player player) {
        return players.get(player.getUniqueId()).spectating();
    }
    public void respawnPlayer(Player player) {
        respawnPlayer(player, 0);
    }
    public void respawnPlayer(Player player, int time) {
        requireRegistered(player);
        if (time < 0 || !isSpectator(player)) {
            throw new IllegalArgumentException("Respawn time cannot be negative!");
        }
        cancelRespawn(player);
        setSpectator(player);
        GameTimer respawnTimer = module.timeManager().newTimer(time).onTick(20, timer -> {
            int remainingSeconds = Math.round(timer.getRemainingTicks() / 20.0f);
            String formattedTimeString = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            module.uiManager().actionbar(player, Component.text("Respawning in: ").append(Component.text(formattedTimeString, NamedTextColor.GREEN)));
        }).onFinish(ignored -> {
            if (isRegistered(player)) {
                player.teleportAsync(getPlayerSpawn(player));
                setNonSpectator(player);
            }
        }).start();
        players.replace(player.getUniqueId(), new PlayerData(isSpectator(player), getPlayerSpawn(player), respawnTimer));
    }
    public void cancelRespawn(Player player) {
        players.get(player.getUniqueId()).respawnTimer().cancel();
    }
    public void setPlayerSpawn(Player player, Location location) {
        players.replace(player.getUniqueId(), new PlayerData(isSpectator(player), location, getRespawnTimer(player)));
        player.setRespawnLocation(location, true);
    }
    public PlayerManager onPlayerRegistered(Consumer<Player> action) {
        Consumer<Player> checkedAction = Objects.requireNonNull(action);
        registrationActions.add(checkedAction);
        return this;
    }
    public boolean isRegistered(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    public Collection<Player> getPlayers() {
        Set<Player> registeredPlayers = new HashSet<>();
        for (UUID uuid : players.keySet()) {
            registeredPlayers.add(Bukkit.getPlayer(uuid));
        }
        return registeredPlayers;
    }
    public Collection<Player> getSpectators() {
        Set<Player> spectators = new HashSet<>();
        for (UUID uuid : players.keySet()) {
            if (players.get(uuid).spectating()) {
                spectators.add(Bukkit.getPlayer(uuid));
            }
        }
        return spectators;
    }
    public Collection<Player> getNonSpectators() {
        Set<Player> nonSpectators = new HashSet<>();
        for (UUID uuid : players.keySet()) {
            if (!players.get(uuid).spectating()) {
                nonSpectators.add(Bukkit.getPlayer(uuid));
            }
        }
        return nonSpectators;
    }
    public Location getPlayerSpawn(Player player) {
        return players.get(player.getUniqueId()).spawnpoint();
    }
    public GameTimer getRespawnTimer(Player player) {
        return players.get(player.getUniqueId()).respawnTimer();
    }

    void registerPlayer(Player player) {
        Objects.requireNonNull(player);
        players.put(player.getUniqueId(), new PlayerData(
            false, 
            module.mapManager().currentWorld().getBukkitWorld().getSpawnLocation(), 
            null
        ));
        player.teleportAsync(getPlayerSpawn(player));
    }
    void unregisterPlayer(Player player) {
        Objects.requireNonNull(player);
        players.remove(player.getUniqueId());
    }

    private void requireRegistered(Player player) {
        if (!isRegistered(player)) {
            throw new IllegalStateException("Specified player is not registered in game");
        }
    }

    private record PlayerData(boolean spectating, Location spawnpoint, GameTimer respawnTimer) {}
}
