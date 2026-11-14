package com.donutsforlife11.doppelganger;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.entity.EntityDeathEvent;

import com.donutsforlife11.donutgame.api.entity.npc.GameNpc;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class DoppelgangerNpcManager {
    private final Doppelganger game;

    private final Map<GameNpc, DoppelgangerNpc> npcs = new HashMap<>();

    public DoppelgangerNpcManager(Doppelganger game) {
        this.game = game;
    }

    public DoppelgangerNpc newNpc(GameLocation location, GamePlayer player) {
        DoppelgangerNpc npc = new DoppelgangerNpc(this, game.world().newNpc(location, player));
        npcs.put(npc.entity(), npc);
        npc.entity().controller().follow(player);
        return npc;
    }

    public void removeNpc(DoppelgangerNpc npc) {
        npcs.remove(npc.entity());
    }

    @GameEventHandler
    public void onNpcDeath(GameEvent<EntityDeathEvent> event) {
        DoppelgangerNpc npc = npcs.remove(event.getEntity());
        if (npc != null) {
            npc.kill();
        }
    }

    Doppelganger game() {
        return game;
    }
}
