package com.donutsforlife11.donutgame.api.player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlayerManager {
    private volatile boolean shutdown;

    private final Plugin plugin;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final List<Consumer<Player>> onRegisteredActions = new CopyOnWriteArrayList<>();

    public PlayerManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean register(Player player, boolean runCallbacks) {
        ensureActive();

        if (!players.add(player.getUniqueId())) {
            return false;
        }

        if (runCallbacks) {
            runCallbacks(onRegisteredActions, player, "onPlayerRegistered");
        }

        return true;
    }
    public boolean isRegistered(Player player) {
        return players.contains(player.getUniqueId());
    }

    public void notifyPlayerEnteredWorld(Player player) {
        if (!isRegistered(player)) {
            return;
        }

        playersInWorld.add(player.getUniqueId());
        runCallbacks(onEnteredWorldActions, player, "onPlayerEnteredWorld");
    }

    private void runCallback(Consumer<Player> action, Player player, String callbackType) {
        try {
            action.accept(player);
        } catch (Throwable throwable) {
            logCallbackFailure(callbackType, throwable);
        }
    }
    private void runCallbacks(List<Consumer<Player>> callbacks, Player player, String callbackType) {
        for (Consumer<Player> action : callbacks) {
            runCallback(action, player, callbackType);
        }
    }
    private void ensureActive() {
        if (shutdown || !plugin.isEnabled()) {
            throw new IllegalStateException("PlayerManager is not active.");
        }
    }
    private void logCallbackFailure(String callbackType, Throwable throwable) {
        plugin.getLogger().severe("Unhandled exception in PlayerManager " + callbackType + " callback:");
        throwable.printStackTrace();
    }
}
