package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.game.GameModule;

final class PlayerRegistry {
    private final GameModule module;
    private final Map<UUID, GamePlayer> playersById = new LinkedHashMap<>();

    PlayerRegistry(GameModule module) {
        this.module = module;
    }

    GamePlayer get(UUID uuid) {
        return playersById.get(uuid);
    }

    boolean contains(UUID uuid) {
        return playersById.containsKey(uuid);
    }

    GamePlayer register(Player player) {
        boolean freshSession = !playersById.containsKey(player.getUniqueId());
        GamePlayer gamePlayer = playersById.computeIfAbsent(player.getUniqueId(), uuid -> new GamePlayer(module, uuid));
        gamePlayer.captureExternalScoreboard(player);
        if (freshSession) {
            gamePlayer.startFreshSession(player);
        } else {
            gamePlayer.remember(player);
        }
        return gamePlayer;
    }

    void remove(Player player) {
        if (player != null) {
            playersById.remove(player.getUniqueId());
        }
    }

    void clear() {
        playersById.clear();
    }

    Collection<GamePlayer> players() {
        return Collections.unmodifiableCollection(playersById.values());
    }

    Collection<GamePlayer> onlinePlayers() {
        Set<GamePlayer> players = new LinkedHashSet<>();
        for (GamePlayer player : playersById.values()) {
            if (player.isOnline()) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    Collection<Player> onlineBukkitPlayers() {
        Set<Player> players = new LinkedHashSet<>();
        for (GamePlayer gamePlayer : playersById.values()) {
            Player player = gamePlayer.bukkitPlayer();
            if (player != null) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    void markOffline(Player player, Predicate<World> ownsWorld) {
        if (player == null) {
            return;
        }
        GamePlayer gamePlayer = playersById.get(player.getUniqueId());
        if (gamePlayer != null && ownsWorld.test(player.getWorld())) {
            gamePlayer.remember(player);
            gamePlayer.markOffline();
        }
    }
}
