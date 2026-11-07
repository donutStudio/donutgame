package com.donutsforlife11.doppelganger;

import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.object.GameEntities;

public class DoppelgangerNpc {
    private final GameNpc entity;
    private final DoppelgangerNpcManager npcManager;

    public DoppelgangerNpc(DoppelgangerNpcManager npcManager, GameNpc npc) {
        this.npcManager = npcManager;
        this.entity = npc;
    }

    public void kill() {
        npcManager.game().world().spawn(entity.location(), GameEntities.LIGHTNING_BOLT);
        npcManager.removeNpc(this);
    }

    GameNpc entity() {
        return entity;
    }
}
