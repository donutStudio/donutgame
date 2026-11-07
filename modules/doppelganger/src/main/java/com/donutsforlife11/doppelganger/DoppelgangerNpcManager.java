package com.donutsforlife11.doppelganger;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.event.entity.EntityDeathEvent;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class DoppelgangerNpcManager {
    private final Doppelganger game;

    private final Set<DoppelgangerNpc> npcs = new HashSet<>();

    public DoppelgangerNpcManager(Doppelganger game) {
        this.game = game;
    }

    public DoppelgangerNpc newNpc(GameLocation location, GamePlayer player) {
        DoppelgangerNpc npc = new DoppelgangerNpc(this, game.world().newNpc(location, player));
        npcs.add(npc);
        npc.entity().controller().follow(player);
        return npc;
    }

    public void removeNpc(DoppelgangerNpc npc) {
        npcs.remove(npc);
    }

    @GameEventHandler
    public void onNpcDeath(GameEvent<EntityDeathEvent> event) {
        GameEntity entity = event.getEntity();
        for (DoppelgangerNpc npc : npcs) {
            if (npc.entity() == entity) {
                npc.kill();
                break;
            } else {
                return;
            }
        }
    }

    Doppelganger game() {
        return game;
    }
}
