package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
                new BorderManager(module),
                plugin.itemService()
            );
            module.borderManager().initialize();
            plugin.getLogger().info("Loading module " + descriptor.id() + " as active game " + index + ".");
            return module.startLoadSequence()
                .thenApply(unused -> module)
                .whenComplete((ignored, throwable) -> {
                    if (throwable == null) {
                        plugin.getLogger().info("Module " + descriptor.id() + " loaded as active game " + index + ".");
                        return;
                    }
                    plugin.getLogger().log(Level.SEVERE, "Failed to load module " + descriptor.id() + " as active game " + index + ".", throwable);
                    activeGames.remove(index, module);
                    freeIndexes.offer(index);
                });
        } catch (Exception e) {
            activeGames.remove(index);
            freeIndexes.offer(index);
            throw e;
        }
    }

    public CompletableFuture<Boolean> unloadModule(int index) {
        GameModule module = activeGames.remove(index);
        if (module == null) {
            return CompletableFuture.completedFuture(false);
        }
        module.onUnload();
        return module.shutdown().thenApply(ignored -> {
            freeIndexes.offer(index);
            return true;
        }).exceptionally(throwable -> {
            freeIndexes.offer(index);
            throw new RuntimeException(throwable);
        });
    }

    public Map<Integer, GameModule> activeGames() {
        return Collections.unmodifiableMap(activeGames);
    }

    public GameModule activeGame(int index) {
        return activeGames.get(index);
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
            unloadModule(gameIndex).join();
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
