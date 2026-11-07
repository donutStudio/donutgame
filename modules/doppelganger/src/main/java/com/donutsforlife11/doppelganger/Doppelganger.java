package com.donutsforlife11.doppelganger;

import java.util.List;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class Doppelganger extends GameModule {
    private final String SPAWN = "spawn";
    private final String NPC_PER_PLAYER = "npc.npc_per_player";

    private DoppelgangerNpcManager npcs;

    @Override
    public void onLoad() {
        npcs = new DoppelgangerNpcManager(this);

        List<GameLocation> spawns = world().getPoints(SPAWN);
        world().setWorldSpawn(spawns.get(0));
        int spawnCount = 0;
        for (GamePlayer player : playerManager().getPlayers()) {
            GameLocation spawn = spawns.get(spawnCount);
            player.teleport(spawn);
            player.setSpawnPoint(spawn);
            npcs.newNpc(spawn, player);
            if (spawnCount++ >= spawns.size()) {
                spawnCount = 0;
            }
        }
    }

    public int npcPerPlayer() {
        return config().getInt(NPC_PER_PLAYER);
    }
}
