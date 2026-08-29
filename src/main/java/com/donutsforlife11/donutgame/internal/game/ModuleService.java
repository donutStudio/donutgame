package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UiManager;

public class ModuleService {
    private final Donutgame plugin;

    public ModuleService(Donutgame plugin) {
        this.plugin = plugin;
    }

    private final Map<Integer, GameModule> activeGames = new HashMap<>();
    private final Queue<Integer> freeIndexes = new PriorityQueue<>();
    private int nextIndex = 0;

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor) throws Exception {
        return loadModule(descriptor, descriptor.createConfig());
    }

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor, YamlConfiguration config) throws Exception {
        return loadModule(descriptor, config, List.of());
    }

    public CompletableFuture<GameModule> loadModule(GameModuleDescriptor descriptor, YamlConfiguration config, Collection<Player> initialPlayers) throws Exception {
        int maxActiveGames = plugin.getConfig().getBoolean("single-world", false)
            ? 1
            : Math.max(1, plugin.getConfig().getInt("max-active-games", 1));
        if (activeGames.size() >= maxActiveGames) {
            throw new IllegalStateException("Cannot load another game; max-active-games is " + maxActiveGames + ".");
        }
        int index = freeIndexes.isEmpty() ? nextIndex++ : freeIndexes.poll();
        GameModule module = descriptor.createModule();
        activeGames.put(index, module);
        try {
            PlayerManager playerManager = new PlayerManager(module);
            module.initialize(
                plugin,
                descriptor,
                index,
                config,
                playerManager,
                new MapManager(module, plugin.mapService(), plugin.worldService()),
                new UiManager(module, plugin),
                new TimeManager(plugin),
                new TeamManager(playerManager),
                new BorderManager(module)
            );
            module.borderManager().initialize();
            plugin.getLogger().info("Loading module " + descriptor.id() + " as active game " + index + ".");
            return module.startLoadSequence(() -> playerManager.register(initialPlayers))
                .thenApply(unused -> module)
                .<CompletableFuture<GameModule>>handle((loaded, throwable) -> {
                    if (throwable == null) {
                        plugin.getLogger().info("Module " + descriptor.id() + " loaded as active game " + index + ".");
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
        } catch (Exception e) {
            activeGames.remove(index);
            freeIndexes.offer(index);
            throw e;
        }
    }

    private CompletableFuture<Void> cleanupFailedLoad(GameModule module, int index) {
        module.setTransitioning(true);
        return module.shutdown().whenComplete((ignored, throwable) -> {
            activeGames.remove(index, module);
            if (!freeIndexes.contains(index)) {
                freeIndexes.offer(index);
            }
        });
    }

    public CompletableFuture<Boolean> unloadModule(int index) {
        GameModule module = activeGames.get(index);
        if (module == null) {
            return CompletableFuture.completedFuture(false);
        }
        module.setTransitioning(true);
        try {
            module.onUnload();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Module " + module.id() + " threw during onUnload(). Continuing shutdown.", throwable);
        }
        return module.shutdown().thenApply(ignored -> {
            activeGames.remove(index, module);
            freeIndexes.offer(index);
            return true;
        }).exceptionally(throwable -> {
            throw new RuntimeException(throwable);
        });
    }

    public Map<Integer, GameModule> activeGames() {
        return Collections.unmodifiableMap(activeGames);
    }

    public GameModule activeGame(int index) {
        return activeGames.get(index);
    }

    public GameModule primaryActiveGame() {
        return activeGames.values().stream().findFirst().orElse(null);
    }

    public boolean singleWorld() {
        return plugin.getConfig().getBoolean("single-world", false);
    }

    public GameModule getGameOfPlayer(Player player) {
        for (GameModule game : activeGames.values()) {
            if (game.playerManager().isRegistered(player)) {
                return game;
            }
        }
        throw new IllegalArgumentException("Could not find specified player registered in any active games");
    }

    public void unloadAll() {
        for (int gameIndex : Set.copyOf(activeGames.keySet())) {
            try {
                unloadModule(gameIndex).join();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unload active game " + gameIndex + ".", exception);
            }
        }
    }

    public int leaveGames(List<Player> players) {
        int removed = 0;
        for (Player player : players) {
            for (GameModule game : activeGames.values()) {
                if (game.playerManager().unregister(player)) {
                    removed++;
                    break;
                }
            }
        }
        return removed;
    }

    public Donutgame plugin() {
        return plugin;
    }
}
