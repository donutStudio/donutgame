package com.donutsforlife11.donutgame.internal.player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class PlayerStateStore {
    private final Map<UUID, Map<String, WorldPlayerState>> states = new HashMap<>();

    public void save(UUID uuid, String worldId, WorldPlayerState playerState) {
        states
            .computeIfAbsent(uuid, ignored -> new HashMap<>())
            .put(worldId, playerState);
    }

    public WorldPlayerState load(UUID uuid, String worldId) {
        Map<String, WorldPlayerState> playerStates = states.get(uuid);

        if (playerStates == null) {
            return null;
        }

        return playerStates.get(worldId);
    }

    public void clearWorld(String worldId) {
        Iterator<Map.Entry<UUID, Map<String, WorldPlayerState>>> iterator = states.entrySet().iterator();

        while (iterator.hasNext()) {
            Map<String, WorldPlayerState> playerStates = iterator.next().getValue();
            playerStates.remove(worldId);

            if (playerStates.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
