package com.donutsforlife11.donutgame.internal.game;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;

public class ModuleManager {
    private final Plugin plugin;

    public ModuleManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private final Map<Integer, GameModule> activeGames = new HashMap<>();
    private final Queue<Integer> freeIndexes = new PriorityQueue<>();
    private int nextId = 0;

    public CompletableFuture<Void> loadModule(GameModuleDescriptor descriptor) throws Exception {
        int id;
        if (freeIndexes.isEmpty()) {
            id = nextId++;
        } else {
            id = freeIndexes.poll();
        }

        GameContext context = new GameContext();
        GameModule module = descriptor.createModule();
        activeGames.put(id, module);
        try {
            initializeModule(module, context, descriptor);
            return module.startLoadSequence();
        } catch (Exception e) {
            throw e;
        }
    }

    public CompletableFuture<Void> unloadModule(int index) {
        GameModule module = activeGames.remove(index);
        if (module == null) {
            return CompletableFuture.completedFuture(null);
        }
        module.onUnload();
        return module.context().shutdown().whenComplete((ignored, error) -> {
            freeIndexes.offer(index);
        });
    }

    private void initializeModule(GameModule module, GameContext context, GameModuleDescriptor descriptor) {
        module.context = context;
        module.id = descriptor.id();
        module.name = descriptor.name();
    }
}
