package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

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
        int index;
        if (freeIndexes.isEmpty()) {
            index = nextIndex++;
        } else {
            index = freeIndexes.poll();
        }
        GameModule module = descriptor.createModule();
        activeGames.put(index, module);
        try {
            module.initialize(
                plugin,
                descriptor, 
                index, 
                new PlayerManager(module), 
                new MapManager(module, plugin.mapService(), plugin.worldService()),
                new UiManager(module, plugin),
                new TimeManager(plugin),
                new TeamManager(),
                new BorderManager(module)
            );
            module.startLoadSequence();
            return CompletableFuture.completedFuture(module);
        } catch (RuntimeException e) {
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
        });
    }

    public Map<Integer, GameModule> activeGames() {
        return Collections.unmodifiableMap(activeGames);
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
        for (int gameIndex : activeGames.keySet()) {
            unloadModule(gameIndex);
        }
    }
}
