package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.ModuleLifecycleState;

public class PlayerManager {
    private final GameModule module;
    private final PlayerRegistry registry;
    private final PlayerTeleporter teleporter;
    private final Set<UUID> leavingPlayers = new LinkedHashSet<>();

    public PlayerManager(GameModule module) {
        this.module = module;
        this.registry = new PlayerRegistry(module);
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
        if (!acceptsPlayerRegistration()) {
            return CompletableFuture.completedFuture(false);
        }
        World world = teleporter.defaultWorld();
        Location destination = world.getSpawnLocation();
        return teleporter.teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " into game " + module.index() + ".");
            }
            leavingPlayers.remove(player.getUniqueId());
            registry.register(player);
            module.teamManager().syncPlayer(registry.get(player.getUniqueId()));
            module.log("Joined player " + player.getName() + " to game world " + world.getName() + ".");
            return true;
        });
    }

    public CompletableFuture<Boolean> leave(Player player) {
        Objects.requireNonNull(player, "player");
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer == null && !ownsWorld(player.getWorld())) {
            return CompletableFuture.completedFuture(false);
        }
        leavingPlayers.add(player.getUniqueId());
        if (gamePlayer != null) {
            gamePlayer.prepareForWorldExit();
            module.uiManager().clear(gamePlayer);
        }
        Location destination = teleporter.fallbackLocation(player.getWorld());
        return teleporter.teleport(player, destination).whenComplete((teleported, throwable) ->
            leavingPlayers.remove(player.getUniqueId())
        ).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " out of game " + module.index() + ".");
            }
            beginExit(player, gamePlayer);
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
                module.uiManager().clear();
                registry.clear();
            });
    }

    public void cleanupRuntimeState() {
        for (GamePlayer player : List.copyOf(registry.players())) {
            Player bukkitPlayer = player.bukkitPlayer();
            if (bukkitPlayer != null) {
                try {
                    player.prepareForRemoval();
                } catch (RuntimeException exception) {
                    module.logError("Failed to fully clean up player " + bukkitPlayer.getName() + " while unloading.", exception);
                }
                module.uiManager().clear(player);
            }
        }
    }

    public boolean owns(Player player) {
        if (player == null) {
            return false;
        }
        return !leavingPlayers.contains(player.getUniqueId())
            && (registry.contains(player.getUniqueId()) || ownsWorld(player.getWorld()));
    }

    public boolean ownsOffline(UUID uuid) {
        return registry.contains(uuid);
    }

    public boolean owns(World world) {
        return ownsWorld(world);
    }

    public boolean ownsWorld(World world) {
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
        return registry.get(player.getUniqueId());
    }

    public Collection<GamePlayer> getPlayers() {
        return registry.players();
    }

    public Collection<GamePlayer> getOnlinePlayers() {
        return registry.onlinePlayers();
    }

    public Collection<GamePlayer> getSpectators() {
        return filteredBySpectatorState(true);
    }

    public Collection<GamePlayer> getNonSpectators() {
        return filteredBySpectatorState(false);
    }

    public void markOffline(Player player) {
        registry.markOffline(player, this::ownsWorld);
    }

    public void detachAfterExternalWorldChange(Player player) {
        if (player == null || ownsWorld(player.getWorld())) {
            return;
        }
        if (leavingPlayers.contains(player.getUniqueId())) {
            return;
        }
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer != null) {
            beginExit(player, gamePlayer);
            leavingPlayers.remove(player.getUniqueId());
            module.log("Detached player " + player.getName() + " after leaving game world " + module.index() + ".");
        }
    }

    public GameModule module() {
        return module;
    }

    private Collection<GamePlayer> filteredBySpectatorState(boolean spectator) {
        Set<GamePlayer> players = new LinkedHashSet<>();
        for (GamePlayer player : registry.players()) {
            if (player.isSpectator() == spectator) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    private void beginExit(Player player, GamePlayer gamePlayer) {
        if (gamePlayer == null) {
            return;
        }
        var team = module.teamManager().getPlayerTeam(gamePlayer);
        if (team != null) {
            team.removePlayer(gamePlayer);
        }
        try {
            gamePlayer.prepareForRemoval();
        } catch (RuntimeException exception) {
            module.plugin().getLogger().log(Level.SEVERE, "Failed to fully clean up player " + player.getName() + " while removing them from game " + module.index() + ".", exception);
        }
        module.uiManager().clear(gamePlayer);
        registry.remove(player);
    }

    private boolean acceptsPlayerRegistration() {
        ModuleLifecycleState state = module.lifecycleState();
        return state == ModuleLifecycleState.LOADED
            || state == ModuleLifecycleState.COUNTDOWN
            || state == ModuleLifecycleState.STARTED;
    }
}
