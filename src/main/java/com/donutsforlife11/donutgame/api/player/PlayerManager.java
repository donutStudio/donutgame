package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class PlayerManager {
    private final GameModule module;
    private final PlayerRegistry registry;
    private final PlayerTeleporter teleporter;

    public PlayerManager(GameModule module) {
        this.module = module;
        this.registry = new PlayerRegistry(this);
        this.teleporter = new PlayerTeleporter(module);
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
        World world = teleporter.defaultWorld();
        Location destination = world.getSpawnLocation();
        return teleporter.teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " into game " + module.index() + ".");
            }
            registry.register(player);
            module.log("Joined player " + player.getName() + " to game world " + world.getName() + ".");
            return true;
        });
    }

    public CompletableFuture<Boolean> leave(Player player) {
        Objects.requireNonNull(player, "player");
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer == null && !owns(player)) {
            return CompletableFuture.completedFuture(false);
        }
        Location destination = teleporter.fallbackLocation(player.getWorld());
        return teleporter.teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " out of game " + module.index() + ".");
            }
            GamePlayer removedPlayer = registry.get(player.getUniqueId());
            if (removedPlayer != null) {
                uiManager().clear(removedPlayer);
            }
            registry.remove(player);
            module.log("Removed player " + player.getName() + " from game " + module.index() + ".");
            return true;
        });
    }

    public CompletableFuture<Void> clear() {
        List<CompletableFuture<Boolean>> leaves = registry.onlineBukkitPlayers().stream()
            .map(this::leave)
            .toList();
        return CompletableFuture.allOf(leaves.toArray(CompletableFuture[]::new))
            .thenRun(() -> {
                uiManager().clear();
                registry.clear();
            });
    }

    public boolean owns(Player player) {
        if (player == null) {
            return false;
        }
        if (owns(player.getWorld())) {
            registry.register(player);
            return true;
        }
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer != null) {
            registry.remove(player);
        }
        return false;
    }

    public boolean ownsOffline(UUID uuid) {
        return registry.contains(uuid);
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
        return registry.get(uuid);
    }

    public GamePlayer getPlayer(Player player) {
        if (player == null) {
            return null;
        }
        if (owns(player)) {
            return registry.get(player.getUniqueId());
        }
        return null;
    }

    public Collection<GamePlayer> getPlayers() {
        return registry.players();
    }

    public Collection<GamePlayer> getOnlinePlayers() {
        return registry.onlinePlayers();
    }

    public void markOffline(Player player) {
        registry.markOffline(player, this::owns);
    }

    public GameModule module() {
        return module;
    }

    UIManager uiManager() {
        return module.uiManager();
    }
}
