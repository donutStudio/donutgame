package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.event.GamePlayerLateJoinEvent;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.ModuleLifecycleState;

public final class PlayerManager {
    private final GameModule module;
    private final PlayerRegistry registry;
    private final PlayerTeleporter teleporter;
    private final Set<UUID> leavingPlayers = new LinkedHashSet<>();

    public PlayerManager(GameModule module, Donutgame plugin) {
        this.module = module;
        this.registry = new PlayerRegistry(module);
        this.teleporter = new PlayerTeleporter(module, plugin);
    }

    CompletableFuture<Integer> joinInitial(Collection<Player> players) {
        return join(players, false);
    }

    CompletableFuture<Integer> join(Collection<Player> players, boolean lateJoin) {
        if (players == null || players.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> joins = players.stream().map(player -> join(player, lateJoin)).toList();
        return CompletableFuture.allOf(joins.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> (int) joins.stream().filter(future -> Boolean.TRUE.equals(future.getNow(false))).count());
    }

    CompletableFuture<Boolean> joinInitial(Player player) {
        return join(player, false);
    }

    CompletableFuture<Boolean> join(Player player, boolean lateJoin) {
        Objects.requireNonNull(player, "player");
        if (!acceptsPlayerRegistration()) {
            return CompletableFuture.completedFuture(false);
        }
        GamePlayer registered = registry.get(player.getUniqueId());
        if (registered != null && registered.isOnline() && ownsWorld(player.getWorld())) {
            return CompletableFuture.completedFuture(true);
        }
        boolean freshSession = !registry.contains(player.getUniqueId());
        World world = GameWorldHandle.requireBukkitWorld(module.world());
        Location destination = world.getSpawnLocation();
        return teleporter.teleport(player, destination).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " into game " + module.index() + ".");
            }
            if (!acceptsPlayerRegistration() || !ownsWorld(world) || player.getWorld() != world || !player.isOnline()) {
                return false;
            }
            leavingPlayers.remove(player.getUniqueId());
            registry.register(player);
            player.setCollidable(module.world().playerCollisionsEnabled());
            GamePlayer gamePlayer = registry.get(player.getUniqueId());
            module.teamManager().syncPlayer(gamePlayer);
            // TODO: Reconnects finish asynchronously too; let the game apply the same join policy after reattachment.
            boolean reconnect = !freshSession && (module.lifecycleState() == ModuleLifecycleState.COUNTDOWN || module.hasStarted());
            if (lateJoin && freshSession || reconnect) {
                applyLateJoinDefaults(player, gamePlayer);
            }
            module.log("Joined player " + player.getName() + " to game world " + world.getName() + ".");
            return true;
        });
    }

    CompletableFuture<Boolean> leave(Player player) {
        Objects.requireNonNull(player, "player");
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer == null && !ownsWorld(player.getWorld())) {
            return CompletableFuture.completedFuture(false);
        }
        if (!ownsWorld(player.getWorld())) {
            finishExit(player, gamePlayer);
            return CompletableFuture.completedFuture(true);
        }
        leavingPlayers.add(player.getUniqueId());
        if (gamePlayer != null) {
            gamePlayer.prepareForWorldExit();
            gamePlayer.restoreExternalScoreboard(player);
            module.uiManager().clear(gamePlayer);
        }
        Location destination = teleporter.fallbackLocation(player.getWorld());
        return teleporter.teleport(player, destination).whenComplete((teleported, throwable) ->
            leavingPlayers.remove(player.getUniqueId())
        ).thenApply(teleported -> {
            if (!teleported) {
                throw new IllegalStateException("Failed to teleport " + player.getName() + " out of game " + module.index() + ".");
            }
            // prepareForWorldExit() already ran before teleport. Do not apply the game
            // snapshot again here: PlayerWorldStateService has just restored the
            // destination world's state during PlayerChangedWorldEvent.
            finishExit(player, gamePlayer);
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
            Player bukkitPlayer = player.playerInCurrentWorld();
            if (bukkitPlayer != null) {
                try {
                    player.prepareForWorldExit();
                } catch (RuntimeException exception) {
                    module.logError("Failed to fully clean up player " + bukkitPlayer.getName() + " while unloading.", exception);
                }
                module.uiManager().clear(player);
            }
        }
    }

    boolean owns(Player player) {
        if (player == null) {
            return false;
        }
        return !leavingPlayers.contains(player.getUniqueId())
            && ownsWorld(player.getWorld());
    }

    public boolean ownsOffline(UUID uuid) {
        return registry.contains(uuid);
    }

    boolean ownsWorld(World world) {
        return world != null && world == GameWorldHandle.bukkitWorld(module.world());
    }

    public GamePlayer getPlayer(UUID uuid) {
        return registry.get(uuid);
    }

    GamePlayer getPlayer(Player player) {
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

    void markOffline(Player player) {
        if (player != null && ownsWorld(player.getWorld())) {
            GamePlayer gamePlayer = registry.get(player.getUniqueId());
            if (gamePlayer != null) {
                gamePlayer.markOffline(player);
            }
        }
    }

    void detachAfterExternalWorldChange(Player player) {
        if (player == null || ownsWorld(player.getWorld())) {
            return;
        }
        if (leavingPlayers.contains(player.getUniqueId())) {
            return;
        }
        GamePlayer gamePlayer = registry.get(player.getUniqueId());
        if (gamePlayer != null) {
            finishExit(player, gamePlayer);
            leavingPlayers.remove(player.getUniqueId());
            module.log("Detached player " + player.getName() + " after leaving game world " + module.index() + ".");
        }
    }

    void captureBeforeWorldSwap() {
        for (GamePlayer player : registry.onlinePlayers()) {
            player.captureBeforeWorldSwap(player.bukkitPlayer());
        }
    }

    void reattachAfterWorldSwap() {
        for (Player player : registry.onlineBukkitPlayers()) {
            GamePlayer gamePlayer = registry.register(player);
            module.teamManager().syncPlayer(gamePlayer);
        }
    }

    public GameModule module() {
        return module;
    }

    private Collection<GamePlayer> filteredBySpectatorState(boolean spectator) {
        Set<GamePlayer> players = new LinkedHashSet<>();
        for (GamePlayer player : registry.players()) {
            boolean effectiveSpectator = player.isSpectator() || !player.isOnline();
            if (effectiveSpectator == spectator) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    private void applyLateJoinDefaults(Player player, GamePlayer gamePlayer) {
        if (gamePlayer == null) {
            return;
        }
        GamePlayerLateJoinEvent event = new GamePlayerLateJoinEvent(player, module, gamePlayer, gamePlayer.spawnPoint());
        Bukkit.getPluginManager().callEvent(event);
        if (event.joinAsSpectator()) {
            gamePlayer.setSpectator(true, event.spectatorLocation() == null ? gamePlayer.spawnPoint() : event.spectatorLocation());
        }
    }

    private void finishExit(Player player, GamePlayer gamePlayer) {
        if (gamePlayer == null) {
            return;
        }
        if (module.acceptsLiveOperations()) {
            Bukkit.getPluginManager().callEvent(new com.donutsforlife11.donutgame.api.event.GamePlayerLeaveEvent(module, gamePlayer));
        }
        var team = module.teamManager().getPlayerTeam(gamePlayer);
        if (team != null) {
            team.removePlayer(gamePlayer);
        }
        gamePlayer.detachAfterWorldExit(player);
        module.uiManager().clear(gamePlayer);
        registry.remove(player);
    }

    private boolean acceptsPlayerRegistration() {
        ModuleLifecycleState state = module.lifecycleState();
        return state == ModuleLifecycleState.LOADING
            || state == ModuleLifecycleState.LOADED
            || state == ModuleLifecycleState.COUNTDOWN
            || state == ModuleLifecycleState.STARTED;
    }
}
