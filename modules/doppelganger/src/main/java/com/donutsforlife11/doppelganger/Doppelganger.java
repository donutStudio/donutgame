package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class Doppelganger extends GameModule {

    static String SPAWN = "spawn";

    private DoppelgangerPlayers players;
    private DoppelgangerNPCs npcs;

    private final List<GameLocation> spawns = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onLoad() {
        this.players = new DoppelgangerPlayers(this);
        registerGameEventHandlers(players);

        world().setWorldSpawn(world().getPoint(SPAWN));
        for (GamePlayer player : playerManager().getPlayers()) {
            player.teleport(world().worldSpawn());
        }
        spawns.addAll(world().getPoints(SPAWN));
        this.npcs = new DoppelgangerNPCs(this);
        npcs.start();
    }

    @Override
    public void onUnload() {
        if (npcs != null) {
            npcs.cleanup();
        }
    }

    public List<GameLocation> spawns() {
        return spawns;
    }
    public GameLocation randomSpawn() {
        if (spawns.isEmpty()) {
            return world().worldSpawn();
        }
        int index = random.nextInt(spawns.size());
        return spawns.get(index);
    }

    public Random random() {
        return random;
    }

    DoppelgangerNPCs npcs() {
        return npcs;
    }
}
