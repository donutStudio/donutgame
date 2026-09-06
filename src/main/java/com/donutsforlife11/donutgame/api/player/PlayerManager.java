package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class PlayerManager {
    private final GameModule module;
    private final Map<UUID, GamePlayer> playersById = new LinkedHashMap<>();

    public PlayerManager(GameModule module) {
        this.module = module;
    }

    public CompletableFuture<Integer> join(Collection<Player> players) {
        if (players == null || players.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> joins = players.stream().map(this::join).toList();
        return CompletableFuture.allOf(joins.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> (int) joins.stream().filter(future -> Boolean.TRUE.equals(future.getNow(false))).count());
    }

    public CompletableFuture<Boolean> join(Player player) {
        Objects.requireNonNull(player, "player");
        World world = defaultBukkitWorld();
        Location destination = world.getSpawnLocation();
        return teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " into game " + module.index() + ".");
            }
            GamePlayer gamePlayer = playersById.computeIfAbsent(player.getUniqueId(), uuid -> new GamePlayer(this, uuid));
            gamePlayer.remember(player);
            module.log("Joined player " + player.getName() + " to game world " + world.getName() + ".");
            return true;
        });
    }

    public CompletableFuture<Boolean> leave(Player player) {
        Objects.requireNonNull(player, "player");
        GamePlayer gamePlayer = playersById.get(player.getUniqueId());
        if (gamePlayer == null && !owns(player)) {
            return CompletableFuture.completedFuture(false);
        }
        Location destination = fallbackLocation(player.getWorld());
        return teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " out of game " + module.index() + ".");
            }
            playersById.remove(player.getUniqueId());
            module.log("Removed player " + player.getName() + " from game " + module.index() + ".");
            return true;
        });
    }

    public CompletableFuture<Void> clear() {
        List<CompletableFuture<Boolean>> leaves = playersById.values().stream()
            .map(player -> player.player())
            .filter(Objects::nonNull)
            .map(this::leave)
            .toList();
        return CompletableFuture.allOf(leaves.toArray(CompletableFuture[]::new))
            .thenRun(playersById::clear);
    }

    public boolean owns(Player player) {
        if (player == null) {
            return false;
        }
        if (owns(player.getWorld())) {
            GamePlayer gamePlayer = playersById.computeIfAbsent(player.getUniqueId(), uuid -> new GamePlayer(this, uuid));
            gamePlayer.remember(player);
            return true;
        }
        GamePlayer gamePlayer = playersById.get(player.getUniqueId());
        if (gamePlayer != null) {
            playersById.remove(player.getUniqueId());
        }
        return false;
    }

    public boolean ownsOffline(UUID uuid) {
        return playersById.containsKey(uuid);
    }

    public boolean owns(World world) {
        if (world == null) {
            return false;
        }
        for (GameWorld gameWorld : module.worlds().values()) {
            World ownedWorld = gameWorld.bukkitWorld();
            if (ownedWorld != null && ownedWorld.equals(world)) {
                return true;
            }
        }
        return false;
    }

    public GamePlayer getPlayer(UUID uuid) {
        return playersById.get(uuid);
    }

    public GamePlayer getPlayer(Player player) {
        if (player == null) {
            return null;
        }
        if (owns(player)) {
            GamePlayer gamePlayer = playersById.computeIfAbsent(player.getUniqueId(), uuid -> new GamePlayer(this, uuid));
            gamePlayer.remember(player);
            return gamePlayer;
        }
        return null;
    }

    public Collection<GamePlayer> getPlayers() {
        return Collections.unmodifiableCollection(playersById.values());
    }

    public Collection<GamePlayer> getOnlinePlayers() {
        Set<GamePlayer> players = new LinkedHashSet<>();
        for (GamePlayer player : playersById.values()) {
            if (player.isOnline()) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    public void markOffline(Player player) {
        GamePlayer gamePlayer = player == null ? null : playersById.get(player.getUniqueId());
        if (player == null) {
            return;
        }
        if (gamePlayer != null && owns(player.getWorld())) {
            gamePlayer.remember(player);
            gamePlayer.markOffline();
        }
    }

    public GameModule module() {
        return module;
    }

    private CompletableFuture<Boolean> teleport(Player player, Location destination) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                destination.getChunk().load();
                player.closeInventory();
                player.teleportAsync(destination).whenComplete((teleported, throwable) ->
                    Bukkit.getScheduler().runTask(module.plugin(), () -> {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                            return;
                        }
                        result.complete(Boolean.TRUE.equals(teleported));
                    })
                );
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(module.plugin(), task);
        }
        return result;
    }

    private World defaultBukkitWorld() {
        GameWorld gameWorld = module.defaultWorld();
        World world = gameWorld == null ? null : gameWorld.bukkitWorld();
        if (world == null) {
            throw new IllegalStateException("Game " + module.index() + " does not have a loaded default world.");
        }
        return world;
    }

    private Location fallbackLocation(World currentWorld) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(currentWorld)) {
                return world.getSpawnLocation();
            }
        }
        throw new IllegalStateException("Cannot move player out of game " + module.index() + " because no destination world is loaded.");
    }
}
