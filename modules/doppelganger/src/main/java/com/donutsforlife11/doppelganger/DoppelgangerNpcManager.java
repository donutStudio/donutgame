package com.donutsforlife11.doppelganger;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.attribute.Attribute;
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
        npc.entity().setAttributeBase(Attribute.MAX_HEALTH, 1);
        npc.entity().setInvulnerable(false);
        npc.entity().setAttributeBase(Attribute.FALL_DAMAGE_MULTIPLIER, 0);
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
