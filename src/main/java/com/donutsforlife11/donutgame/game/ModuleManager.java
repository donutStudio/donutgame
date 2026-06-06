package com.donutsforlife11.donutgame.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;

import com.donutsforlife11.donutgame.api.ModuleApi;

public class ModuleManager {
    private Map<Integer, GameModule> activeGames = new HashMap<>();
    private Map<Integer, GameContext> contexts = new HashMap<>();

    private int nextId = 0;
    private final Queue<Integer> freeIndexes = new LinkedList<>();
    private final Function<Integer, GameContext> contextFactory;

    public ModuleManager(Function<Integer, GameContext> contextFactory) {
        this.contextFactory = contextFactory;
    }

    public int loadModule(GameModule module) {
        int id;

        if (!freeIndexes.isEmpty()) {
            id = freeIndexes.poll();
        } else {
            id = nextId++;
        }

        GameContext context = contextFactory.apply(id);

        activeGames.put(id, module);
        contexts.put(id, context);
        module.onLoad(context);

        return id;
    }

    public void unloadModule(int moduleIndex) {
        GameModule module = activeGames.remove(moduleIndex);
        GameContext context = contexts.remove(moduleIndex);

        if (module != null) {
            module.onUnload();
            if (context != null) {
                if (context instanceof ModuleApi api) {
                    api.shutdown();
                } else {
                    context.teamManager().clear();
                }
            }
            freeIndexes.offer(moduleIndex);
        }
    }

    public Map<Integer, GameModule> getActiveGames() {
        return Collections.unmodifiableMap(activeGames);
    }
}
