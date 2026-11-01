package com.donutsforlife11.doppelganger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.entity.EntityDeathEvent;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.object.GameBlocks;
import com.donutsforlife11.donutgame.api.object.GameEntities;

public class DoppelgangerPlayers {

    private final Doppelganger game;
    private final Set<UUID> npcs = new HashSet<>();

    public DoppelgangerPlayers(Doppelganger game) {
        this.game = game;
    }

    public void newNpc() {
        game.world().spawn(game.randomSpawn(), GameEntities.MANNEQUIN, entity -> npcs.add(entity.uuid()));
    }
    public void killNpc(GameEntity entity) {
        if (entity == null || !entity.is(GameEntities.MANNEQUIN)) {
            return;
        }
        npcs.remove(entity.uuid());
        if (game.npcs() != null) {
            game.npcs().remove(entity);
        }
    }
    @GameEventHandler
    public void onNpcDeath(GameEvent<EntityDeathEvent> event) {
        GameEntity entity = event.getEntity();

        if (entity == null || game.npcs() == null) {
            return;
        }

        if (!game.npcs().owns(entity)) {
            return;
        }

        game.npcs().remove(entity);

        GameLocation location = event.getLocation();

        if (location != null) {
            game.world().setBlock(
                location,
                GameBlocks.PLAYER_HEAD
            );
        }
    }
}
