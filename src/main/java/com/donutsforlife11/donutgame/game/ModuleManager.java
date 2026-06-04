package com.donutsforlife11.donutgame.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class ModuleManager {
    private Map<Integer, GameModule> activeGames = new HashMap<>();

    private int nextId = 0;
    private final Queue<Integer> freeIndexes = new LinkedList<>();
    private final GameContext context;

    public ModuleManager(GameContext context) {
        this.context = context;
    }

    public int loadModule(GameModule module) {
        int id;

        if (!freeIndexes.isEmpty()) {
            id = freeIndexes.poll();
        } else {
            id = nextId++;
        }

        activeGames.put(id, module);
        module.onLoad(context);

        return id;
    }

    public void unloadModule(int moduleIndex) {
        GameModule module = activeGames.remove(moduleIndex);

        if (module != null) {
            freeIndexes.offer(moduleIndex);
            module.onUnload();
        }
    }

    public Map<Integer, GameModule> getActiveGames() {
        return Collections.unmodifiableMap(activeGames);
    }
}
