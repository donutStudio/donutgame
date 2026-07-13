package com.donutsforlife11.voidwars;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class VoidWars extends GameModule {
    @Override
    public void beforeLoad() {
        mapManager().setMap("classic_enhanced");
    }
    public void onLoad() {
        playerManager().plugin().getLogger().info("sahur");
    }
    public void onUnload() {
        playerManager().plugin().getLogger().info("ruhas");
    }
}
