package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

public class ModuleService {
    private final Donutgame plugin;
    private final Map<Integer, GameModule> activeGames = new HashMap<>();
    private final Queue<Integer> freeIndexes = new PriorityQueue<>();
    private int nextIndex;
    private Integer lockedGameIndex;

    public ModuleService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor) throws ReflectiveOperationException {
        return loadModule(descriptor, descriptor.createConfig());
    }

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor, YamlConfiguration config) throws ReflectiveOperationException {
        return loadModule(descriptor, config, java.util.List.of());
    }

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor, YamlConfiguration config, java.util.Collection<Player> initialPlayers) throws ReflectiveOperationException {
        int maxActiveGames = Math.max(1, plugin.getConfig().getInt("max_active_games", plugin.getConfig().getInt("max-active-games", 1)));
        if (activeGames.size() >= maxActiveGames) {
            throw new IllegalStateException("Cannot load another game; max_active_games is " + maxActiveGames + ".");
        }

        GameModule module = descriptor.createModule();
        int index = freeIndexes.isEmpty() ? nextIndex++ : freeIndexes.poll();
        activeGames.put(index, module);

        try {
            MapManager mapManager = new MapManager(module, plugin.mapService(), plugin.worldService());
            PlayerManager playerManager = new PlayerManager(module);
            TeamManager teamManager = new TeamManager(playerManager);
            TimeManager timeManager = new TimeManager(module);
            UIManager uiManager = new UIManager(module);
            BorderManager borderManager = new BorderManager(module);
            module.initialize(plugin, descriptor, index, config, 
                mapManager, 
                playerManager, 
                teamManager,
                timeManager,
                uiManager,
                borderManager
            );
            teamManager.initialize();
            plugin.getLogger().info("Loading module " + descriptor.id() + " as active game " + index + ".");
            return module.startLoadSequence(() -> playerManager.joinInitial(initialPlayers))
                .thenApply(ignored -> {
                    plugin.getLogger().info("Loaded module " + descriptor.id() + " as active game " + index + ".");
                    return module;
                })
                .<CompletableFuture<GameModule>>handle((loaded, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(loaded);
                    }
                    plugin.getLogger().log(Level.SEVERE, "Failed to load module " + descriptor.id() + " as active game " + index + ".", throwable);
                    return cleanupFailedLoad(module, index).handle((ignored, cleanupThrowable) -> {
                        if (cleanupThrowable != null) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to clean up module " + descriptor.id() + " after load failure.", cleanupThrowable);
                        }
                        throw new CompletionException(throwable);
                    });
                })
                .thenCompose(future -> future);
        } catch (RuntimeException exception) {
            activeGames.remove(index, module);
            freeIndexes.offer(index);
            throw exception;
        }
    }

    public CompletableFuture<ModuleUnloadResult> unloadModule(int index) {
        GameModule module = activeGames.get(index);
        if (module == null) {
            return CompletableFuture.completedFuture(ModuleUnloadResult.missing());
        }

        boolean[] hadErrors = new boolean[1];
        try {
            module.onUnload();
        } catch (Throwable throwable) {
            hadErrors[0] = true;
            plugin.getLogger().log(Level.SEVERE, "Module " + module.id() + " threw during onUnload(). Continuing shutdown.", throwable);
        }

        return module.shutdown()
            .handle((ignored, throwable) -> {
                if (throwable != null) {
                    hadErrors[0] = true;
                    plugin.getLogger().log(Level.SEVERE, "Module " + module.id() + " had errors during shutdown. Removing it from active games anyway.", throwable);
                }
                activeGames.remove(index, module);
                if (lockedGameIndex != null && lockedGameIndex == index) {
                    lockedGameIndex = null;
                }
                freeIndexes.offer(index);
                return ModuleUnloadResult.unloaded(hadErrors[0]);
            });
    }

    public Map<Integer, GameModule> activeGames() {
        return Collections.unmodifiableMap(activeGames);
    }

    public Donutgame plugin() {
        return plugin;
    }

    public GameModule activeGame(int index) {
        return activeGames.get(index);
    }

    public Integer lockedGameIndex() {
        return lockedGameIndex;
    }

    public GameModule lockedGame() {
        return lockedGameIndex == null ? null : activeGames.get(lockedGameIndex);
    }

    public CompletableFuture<Integer> lockGame(int index) {
        GameModule game = activeGames.get(index);
        if (game == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No active game exists at index " + index + "."));
        }
        lockedGameIndex = index;
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        return joinLockedGame(players);
    }

    public void unlockGame() {
        lockedGameIndex = null;
    }

    public GameModule getGameOfPlayer(Player player) {
        if (player == null) {
            return null;
        }
        for (GameModule game : activeGames.values()) {
            if (game.playerManager().owns(player)) {
                return game;
            }
        }
        return null;
    }

    public GameModule getOfflineGameOfPlayer(java.util.UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (GameModule game : activeGames.values()) {
            if (game.playerManager().ownsOffline(uuid)) {
                return game;
            }
        }
        return null;
    }

    public CompletableFuture<Integer> leaveGames(java.util.Collection<Player> players) {
        CompletableFuture<Integer> total = CompletableFuture.completedFuture(0);
        for (Player player : players) {
            GameModule game = getGameOfPlayer(player);
            if (game == null) {
                continue;
            }
            total = total.thenCompose(count -> game.playerManager().leave(player).thenApply(left -> count + (left ? 1 : 0)));
        }
        return total;
    }

    public CompletableFuture<Boolean> joinLockedGame(Player player) {
        GameModule lockedGame = lockedGame();
        if (lockedGame == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> leavePrevious = CompletableFuture.completedFuture(false);
        GameModule currentGame = getGameOfPlayer(player);
        if (currentGame == lockedGame) {
            return CompletableFuture.completedFuture(false);
        }
        if (currentGame != null && currentGame != lockedGame) {
            leavePrevious = currentGame.playerManager().leave(player);
        }
        GameModule offlineGame = getOfflineGameOfPlayer(player.getUniqueId());
        if (offlineGame != null && offlineGame != lockedGame) {
            leavePrevious = leavePrevious.thenCompose(ignored -> offlineGame.playerManager().leave(player));
        }
        boolean reconnectingToLockedGame = lockedGame.playerManager().ownsOffline(player.getUniqueId());
        return leavePrevious.thenCompose(ignored -> lockedGame.playerManager().join(player, !reconnectingToLockedGame));
    }

    public CompletableFuture<Integer> joinLockedGame(java.util.Collection<Player> players) {
        CompletableFuture<Integer> total = CompletableFuture.completedFuture(0);
        for (Player player : players) {
            total = total.thenCompose(count -> joinLockedGame(player).thenApply(joined -> count + (joined ? 1 : 0)));
        }
        return total;
    }

    public void unloadAll() {
        for (int index : Set.copyOf(activeGames.keySet())) {
            try {
                CompletableFuture<ModuleUnloadResult> unload = unloadModule(index);
                if (Bukkit.isPrimaryThread()) {
                    unload.exceptionally(exception -> {
                        plugin.getLogger().log(Level.SEVERE, "Failed to unload active game " + index + ".", exception);
                        return ModuleUnloadResult.unloaded(true);
                    });
                } else {
                    unload.join();
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unload active game " + index + ".", exception);
            }
        }
    }

    private CompletableFuture<Void> cleanupFailedLoad(GameModule module, int index) {
        return module.shutdown().whenComplete((ignored, throwable) -> {
            activeGames.remove(index, module);
            if (lockedGameIndex != null && lockedGameIndex == index) {
                lockedGameIndex = null;
            }
            if (!freeIndexes.contains(index)) {
                freeIndexes.offer(index);
            }
        });
    }
}
