package com.donutsforlife11.doppelganger;

import java.util.List;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class Doppelganger extends GameModule {
    private final String SPAWN = "spawn";
    private final String NPC_PER_PLAYER = "npc.npc_per_player";
    private final String SHERIFF_COUNT = "sheriff_count";

    private DoppelgangerNpcManager npcs;
    private DoppelgangerPlayers players;

    @Override
    public void onLoad() {
        npcs = new DoppelgangerNpcManager(this);
        players = new DoppelgangerPlayers(this);
        registerGameEventHandlers(npcs);

        world().setSpawnLocation(world().getPoint(SPAWN));
        
        world().gamerules().pvp(false);
        world().gamerules().fallDamage(false);
        players.loadPlayers();
    }

    @Override
    public void onStart() {
        world().gamerules().pvp(true);
        players.startPlayers();
    }

    public int npcPerPlayer() {
        return config().getInt(NPC_PER_PLAYER);
    }
    public int sheriffCount() {
        return config().getInt(SHERIFF_COUNT);
    }
    public List<GameLocation> spawns() {
        return world().getPoints(SPAWN);
    }

    public DoppelgangerNpcManager npcs() {
        return npcs;
    }
}
