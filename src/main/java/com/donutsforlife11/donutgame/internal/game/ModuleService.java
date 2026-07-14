package com.donutsforlife11.donutgame.internal.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
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
                descriptor, 
                index, 
                new PlayerManager(module), 
                new MapManager(module, plugin.mapService(), plugin.worldService()),
                new UiManager(module, plugin)
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
}
