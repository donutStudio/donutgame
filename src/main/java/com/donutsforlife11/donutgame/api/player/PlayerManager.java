package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class PlayerManager {
    private final GameModule module;

    private final List<Consumer<GamePlayer>> registrationActions = new ArrayList<>();
    private final Set<GamePlayer> players = new LinkedHashSet<>();

    public PlayerManager(GameModule module) {
        this.module = module;
    }

    public PlayerManager onPlayerRegistered(Consumer<GamePlayer> action) {
        registrationActions.add(Objects.requireNonNull(action));
        return this;
    }

    public boolean isRegistered(Entity entity) {
        return entity != null && getPlayer(entity.getUniqueId()) != null;
    }

    public boolean isRegistered(Player player) {
        return isRegistered((Entity) player);
    }

    public boolean isRegistered(GamePlayer player) {
        return player != null && players.contains(player);
    }

    public Collection<GamePlayer> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public Collection<GamePlayer> getSpectators() {
        Set<GamePlayer> spectators = new LinkedHashSet<>();
        for (GamePlayer player : players) {
            if (player.isSpectator()) {
                spectators.add(player);
            }
        }
        return Collections.unmodifiableSet(spectators);
    }

    public Collection<GamePlayer> getNonSpectators() {
        Set<GamePlayer> nonSpectators = new LinkedHashSet<>();
        for (GamePlayer player : players) {
            if (!player.isSpectator()) {
                nonSpectators.add(player);
            }
        }
        return Collections.unmodifiableSet(nonSpectators);
    }

    public GamePlayer getPlayer(UUID uuid) {
        for (GamePlayer player : players) {
            if (player.uuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    public GamePlayer getPlayer(Player player) {
        return player == null ? null : getPlayer(player.getUniqueId());
    }

    void registerPlayer(Player player) {
        Objects.requireNonNull(player);
        GamePlayer gamePlayer = new GamePlayer(
            this,
            module.world(),
            player.getUniqueId(),
            module.world().spawnLocation()
        );
        players.add(gamePlayer);
        gamePlayer.teleport(gamePlayer.respawnLocation());
        for (Consumer<GamePlayer> action : registrationActions) {
            action.accept(gamePlayer);
        }
    }

    void unregisterPlayer(Player player) {
        Objects.requireNonNull(player);
        GamePlayer gamePlayer = getPlayer(player);
        if (gamePlayer != null) {
            gamePlayer.cancelRespawn();
            players.remove(gamePlayer);
        }
    }

    public GameModule module() {
        return module;
    }
}
