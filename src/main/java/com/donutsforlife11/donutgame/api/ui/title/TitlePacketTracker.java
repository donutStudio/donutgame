package com.donutsforlife11.donutgame.api.ui.title;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

public class TitlePacketTracker {
    private static final long DEFAULT_ACTIVE_MS = 3500;

    private final Map<UUID, Long> activeTitles = new ConcurrentHashMap<>();

    public boolean hasActiveTitle(Player player) {
        if (player == null) {
            return false;
        }
        Long expiresAt = activeTitles.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            activeTitles.remove(player.getUniqueId(), expiresAt);
            return false;
        }
        return true;
    }

    public void markTitle(Player player) {
        if (player != null) {
            activeTitles.put(player.getUniqueId(), System.currentTimeMillis() + DEFAULT_ACTIVE_MS);
        }
    }

    public void clear(Player player) {
        if (player != null) {
            activeTitles.remove(player.getUniqueId());
        }
    }

    public void clear() {
        activeTitles.clear();
    }
}
