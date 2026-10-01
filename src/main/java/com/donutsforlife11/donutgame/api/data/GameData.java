package com.donutsforlife11.donutgame.api.data;

import java.io.File;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameData extends GameDataBase {
    public GameData(GameModule module, File moduleJar) {
        super(module, moduleJar);
    }
}
