package com.donutsforlife11.donutgame.api.data;

import java.io.File;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameData extends GameDataBase {
    private final GameModule module;
    private com.donutsforlife11.donutgame.internal.game.GameInteractionService interactions;
    public GameData(GameModule module, File moduleDirectory) {
        super(module, moduleDirectory);
        this.module = module;
    }

    public void interactEvent(String id, com.donutsforlife11.donutgame.api.item.GameItemInteraction action) {
        com.donutsforlife11.donutgame.internal.game.GameScope.requireActive(module);
        if (interactions == null) interactions = new com.donutsforlife11.donutgame.internal.game.GameInteractionService(module);
        interactions.register(id, action);
    }

    public void clearInteractions() {
        if (interactions != null) interactions.close();
    }
}
