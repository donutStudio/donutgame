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
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerManager {
    private final Plugin plugin;
    private final TimeManager timeManager;
    private final UIManager uiManager;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> playersInWorld = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> spawns = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> nonSpectatorModes = new ConcurrentHashMap<>();
    private final Map<UUID, GameTimer> respawnTasks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRespawns = ConcurrentHashMap.newKeySet();
    private final List<Consumer<Player>> onRegisteredActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<Player>> onEnteredWorldActions = new CopyOnWriteArrayList<>();

    private volatile boolean shutdown;

    public PlayerManager(Plugin plugin, TimeManager timeManager, UIManager uiManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.uiManager = uiManager;
    }

    public Collection<Player> getPlayers() {
        return getOnlinePlayers(players);
    }

    public void setSpectator(Player player) {
        requireRegistered(player);

        if (player.getGameMode() != GameMode.SPECTATOR) {
            nonSpectatorModes.put(player.getUniqueId(), player.getGameMode());
        }

        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void setNonSpectator(Player player) {
        requireRegistered(player);
        spectators.remove(player.getUniqueId());
        GameMode gameMode = nonSpectatorModes.remove(player.getUniqueId());
        player.setGameMode(gameMode == null ? GameMode.ADVENTURE : gameMode);
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
        player.setRespawnLocation(location, true);
        spawns.put(player.getUniqueId(), requireLocation(location));
    }

    public Location getPlayerSpawn(Player player) {
        requireRegistered(player);
        Location location = spawns.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public void respawnPlayer(Player player) {
        respawnPlayer(player, 0);
    }

    public void respawnPlayer(Player player, int time) {
        requireRegistered(player);

        if (time < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative.");
        }

        if (time > 0 || player.isDead()) {
            setSpectator(player);
        }

        cancelRespawn(player);

        if (time == 0) {
            triggerRespawn(player);
            return;
        }

        // New
        GameTimer task = timeManager.createTimer(time).whileRunning(20, timer -> {
            int remainingSeconds = timer.getRemainingSeconds();
            String formattedTimeString = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            uiManager.actionbar(player, Component.text("Respawning in: ").append(Component.text(formattedTimeString, NamedTextColor.GREEN)));
        }).onEnd(end -> {
            if (isRegistered(player)) {
                respawnTasks.remove(player.getUniqueId());
                triggerRespawn(player);
            }
        }).start();

        respawnTasks.put(player.getUniqueId(), task);
    }

    public void cancelRespawn(Player player) {
        pendingRespawns.remove(player.getUniqueId());

        GameTimer task = respawnTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public PlayerManager onPlayerRegistered(Consumer<Player> action) {
        ensureActive();
        Consumer<Player> checkedAction = Objects.requireNonNull(action, "action");
        onRegisteredActions.add(checkedAction);

        for (Player player : getPlayers()) {
            runCallback(checkedAction, player, "onPlayerRegistered");
        }

        return this;
    }

    public PlayerManager onPlayerEnteredWorld(Consumer<Player> action) {
        ensureActive();
        Consumer<Player> checkedAction = Objects.requireNonNull(action, "action");
        onEnteredWorldActions.add(checkedAction);

        for (Player player : getOnlinePlayers(playersInWorld)) {
            runCallback(checkedAction, player, "onPlayerEnteredWorld");
        }

        return this;
    }

    public boolean register(Player player, boolean runCallbacks) {
        ensureActive();

        if (!players.add(player.getUniqueId())) {
            return false;
        }

        if (runCallbacks) {
            runCallbacks(onRegisteredActions, player, "onPlayerRegistered");
        }

        return true;
    }

    public void unregister(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) {
            return;
        }

        playersInWorld.remove(uuid);
        spectators.remove(uuid);
        spawns.remove(uuid);
        nonSpectatorModes.remove(uuid);
        pendingRespawns.remove(uuid);
        cancelRespawn(player);
    }

    public boolean isRegistered(Player player) {
        return players.contains(player.getUniqueId());
    }

    public void handleDisconnect(Player player) {
        if (isRegistered(player)) {
            unregister(player);
        }
    }

    public void handleDeath(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        cancelRespawn(player);
        setSpectator(player);
    }

    public void applyRespawnLocation(Player player, org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!isRegistered(player)) {
            return;
        }

        Location spawn = spawns.get(player.getUniqueId());
        if (spawn != null) {
            event.setRespawnLocation(spawn.clone());
        }

        if (pendingRespawns.remove(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isRegistered(player)) {
                    setNonSpectator(player);
                }
            });
        }
    }

    public void shutdown() {
        shutdown = true;
        List.copyOf(respawnTasks.values()).forEach(GameTimer::cancel);
        respawnTasks.clear();
        pendingRespawns.clear();
        players.clear();
        playersInWorld.clear();
        spectators.clear();
        spawns.clear();
        nonSpectatorModes.clear();
    }

    public void notifyPlayerEnteredWorld(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        playersInWorld.add(player.getUniqueId());
        runCallbacks(onEnteredWorldActions, player, "onPlayerEnteredWorld");
    }

    public void notifyPlayerLeftWorld(Player player) {
        playersInWorld.remove(player.getUniqueId());
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
        setNonSpectator(player);

        Location spawn = spawns.get(player.getUniqueId());
        if (spawn != null) {
            player.teleportAsync(spawn.clone());
        }
    }

    private void triggerRespawn(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        if (player.isDead()) {
            pendingRespawns.add(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, player.spigot()::respawn);
            return;
        }

        teleportToSpawn(player);
    }

    private void runCallbacks(List<Consumer<Player>> callbacks, Player player, String callbackType) {
        for (Consumer<Player> action : callbacks) {
            runCallback(action, player, callbackType);
        }
    }

    private void runCallback(Consumer<Player> action, Player player, String callbackType) {
        try {
            action.accept(player);
        } catch (Throwable throwable) {
            logCallbackFailure(callbackType, throwable);
        }
    }

    private void logCallbackFailure(String callbackType, Throwable throwable) {
        plugin.getLogger().severe("Unhandled exception in PlayerManager " + callbackType + " callback:");
        throwable.printStackTrace();
    }
}
