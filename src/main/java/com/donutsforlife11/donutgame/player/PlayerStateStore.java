package com.donutsforlife11.donutgame.player;

import java.util.HashMap;
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

    public boolean has(UUID uuid, String worldId) {
        Map<String, WorldPlayerState> playerStates = states.get(uuid);
        return playerStates != null && playerStates.containsKey(worldId);
    }
}