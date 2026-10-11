package com.donutsforlife11.doppelganger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class Doppelganger extends GameModule {

    static String SPAWN = "spawn";

    private DoppelgangerPlayers players;

    private final List<GameLocation> spawns = new ArrayList<>();

    @Override
    public void onLoad() {
        this.players = new DoppelgangerPlayers(this);
        registerGameEventHandlers(players);

        spawns.addAll(world().getPoints(SPAWN));
    }

    public List<GameLocation> spawns() {
        return spawns;
    }
    public GameLocation randomSpawn() {
        Random random = new Random();
        int index = random.nextInt(spawns.size());
        return spawns.get(index);
    }
}
