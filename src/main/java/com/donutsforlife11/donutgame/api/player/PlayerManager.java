package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.api.player.PlayerManager;

public class PlayerManager {
    private final Plugin plugin;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> spawns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new ConcurrentHashMap<>();

    private final List<Consumer<Player>> onRegisteredActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<Player>> onDeathActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<Player>> onDisconnectActions = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Player, Player>> onKillActions = new CopyOnWriteArrayList<>();

    private volatile boolean shutdown;

    public PlayerManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Player> getPlayers() {
        return getOnlinePlayers(players);
    }

    public void setSpectator(Player player) {
        requireRegistered(player);
        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void setNonSpectator(Player player) {
        requireRegistered(player);
        spectators.remove(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public Collection<Player> getSpectators() {
        return getOnlinePlayers(spectators);
    }

    public Collection<Player> getNonSpectators() {
        return players.stream()
            .filter(uuid -> !spectators.contains(uuid))
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .toList();
    }

    public void setPlayerSpawn(Player player, Location location) {
        requireRegistered(player);
        spawns.put(player.getUniqueId(), requireLocation(location));
    }

    public Location getPlayerSpawn(Player player) {
        requireRegistered(player);

        Location location = spawns.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public void respawnPlayer(Player player) {
        requireRegistered(player);
        cancelRespawn(player);

        if (player.isDead()) {
            BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
                if (isRegistered(player) && player.isDead()) {
                    player.spigot().respawn();
                }
                respawnTasks.remove(player.getUniqueId());
            });
            respawnTasks.put(player.getUniqueId(), task);
            return;
        }

        teleportToSpawn(player);
    }

    public void respawnPlayer(Player player, int time) {
        requireRegistered(player);

        if (time < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative.");
        }

        if (time == 0) {
            respawnPlayer(player);
            return;
        }

        cancelRespawn(player);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isRegistered(player)) {
                respawnTasks.remove(player.getUniqueId());
                return;
            }

            if (player.isDead()) {
                player.spigot().respawn();
            } else {
                teleportToSpawn(player);
            }

            respawnTasks.remove(player.getUniqueId());
        }, time);

        respawnTasks.put(player.getUniqueId(), task);
    }

    public void cancelRespawn(Player player) {
        BukkitTask task = respawnTasks.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
        }
    }

    public PlayerManager onPlayerRegistered(Consumer<Player> action) {
        onRegisteredActions.add(Objects.requireNonNull(action, "action"));
        return this;
    }

    public PlayerManager onPlayerDeath(Consumer<Player> action) {
        onDeathActions.add(Objects.requireNonNull(action, "action"));
        return this;
    }

    public PlayerManager onPlayerDisconnect(Consumer<Player> action) {
        onDisconnectActions.add(Objects.requireNonNull(action, "action"));
        return this;
    }

    public PlayerManager onPlayerKill(BiConsumer<Player, Player> action) {
        onKillActions.add(Objects.requireNonNull(action, "action"));
        return this;
    }

    public boolean register(Player player, boolean runCallbacks) {
        ensureActive();

        if (!players.add(player.getUniqueId())) {
            return false;
        }

        if (runCallbacks) {
            runPlayerCallbacks(onRegisteredActions, player, "onPlayerRegistered");
        }

        return true;
    }

    public void unregister(Player player) {
        UUID uuid = player.getUniqueId();

        if (!players.remove(uuid)) {
            return;
        }

        spectators.remove(uuid);
        spawns.remove(uuid);
        cancelRespawn(player);
    }

    public boolean isRegistered(Player player) {
        return players.contains(player.getUniqueId());
    }

    public void handleDeath(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        runPlayerCallbacks(onDeathActions, player, "onPlayerDeath");
    }

    public void handleDisconnect(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        runPlayerCallbacks(onDisconnectActions, player, "onPlayerDisconnect");
        unregister(player);
    }

    public void handleKill(Player attacker, Player victim) {
        if (!isRegistered(attacker) || !isRegistered(victim)) {
            return;
        }

        for (BiConsumer<Player, Player> action : onKillActions) {
            try {
                action.accept(attacker, victim);
            } catch (Throwable throwable) {
                logCallbackFailure("onPlayerKill", throwable);
            }
        }
    }

    public void applyRespawnLocation(Player player, org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!isRegistered(player)) {
            return;
        }

        Location spawn = spawns.get(player.getUniqueId());

        if (spawn != null) {
            event.setRespawnLocation(spawn.clone());
        }
    }

    public void shutdown() {
        shutdown = true;

        List.copyOf(respawnTasks.values()).forEach(BukkitTask::cancel);
        respawnTasks.clear();
        players.clear();
        spectators.clear();
        spawns.clear();
    }

    private void ensureActive() {
        if (shutdown || !plugin.isEnabled()) {
            throw new IllegalStateException("PlayerManager is not active.");
        }
    }

    private void requireRegistered(Player player) {
        ensureActive();

        if (!isRegistered(player)) {
            throw new IllegalStateException("Player is not registered to this game.");
        }
    }

    private Location requireLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null.");
        }

        return location.clone();
    }

    private Collection<Player> getOnlinePlayers(Collection<UUID> uuids) {
        return uuids.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .toList();
    }

    private void teleportToSpawn(Player player) {
        Location spawn = spawns.get(player.getUniqueId());

        if (spawn != null) {
            player.teleportAsync(spawn.clone());
        }
    }

    private void runPlayerCallbacks(List<Consumer<Player>> callbacks, Player player, String callbackType) {
        for (Consumer<Player> action : callbacks) {
            try {
                action.accept(player);
            } catch (Throwable throwable) {
                logCallbackFailure(callbackType, throwable);
            }
        }
    }

    private void logCallbackFailure(String callbackType, Throwable throwable) {
        plugin.getLogger().severe("Unhandled exception in PlayerManager " + callbackType + " callback:");
        throwable.printStackTrace();
    }
}
