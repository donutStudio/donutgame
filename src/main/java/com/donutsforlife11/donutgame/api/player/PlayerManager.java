package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class PlayerManager {
    private final GameModule module;
    private final List<Consumer<GamePlayer>> registrationActions = new ArrayList<>();
    private final List<Consumer<GamePlayer>> unregistrationActions = new ArrayList<>();
    private final Set<GamePlayer> players = new LinkedHashSet<>();
    private final Map<UUID, GamePlayer> playersById = new LinkedHashMap<>();
    private final Set<UUID> pendingPlayers = new LinkedHashSet<>();

    public PlayerManager(GameModule module) {
        this.module = module;
    }

    public PlayerManager onPlayerRegistered(Consumer<GamePlayer> action) {
        registrationActions.add(Objects.requireNonNull(action));
        return this;
    }

    public PlayerManager onPlayerUnregistered(Consumer<GamePlayer> action) {
        unregistrationActions.add(Objects.requireNonNull(action));
        return this;
    }

    public boolean isRegistered(Entity entity) {
        return entity != null && playersById.containsKey(entity.getUniqueId());
    }

    public boolean isRegistered(Player player) {
        return isRegistered((Entity) player);
    }

    public boolean isRegistered(GamePlayer player) {
        return player != null && playersById.get(player.uuid()) == player;
    }

    public Collection<GamePlayer> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public Collection<GamePlayer> getSpectators() {
        return filtered(true);
    }

    public Collection<GamePlayer> getNonSpectators() {
        return filtered(false);
    }

    public GamePlayer getPlayer(UUID uuid) {
        return playersById.get(uuid);
    }

    public GamePlayer getPlayer(Player player) {
        return player == null ? null : getPlayer(player.getUniqueId());
    }

    public boolean register(Player player) {
        Objects.requireNonNull(player);
        UUID uuid = player.getUniqueId();
        if (playersById.containsKey(uuid) || !pendingPlayers.add(uuid) && module.world() == null) {
            module.log("Skipped registration for " + player.getName() + " because they are already registered or pending.");
            return false;
        }
        if (module.world() == null) {
            module.log("Queued player " + player.getName() + " for registration until the game world is ready.");
            return true;
        }
        pendingPlayers.remove(uuid);
        registerNow(player);
        return true;
    }

    public int register(Collection<Player> players) {
        int registered = 0;
        for (Player player : players) {
            if (register(player)) {
                registered++;
            }
        }
        return registered;
    }

    public boolean unregister(Player player) {
        Objects.requireNonNull(player);
        pendingPlayers.remove(player.getUniqueId());
        GamePlayer gamePlayer = playersById.remove(player.getUniqueId());
        if (gamePlayer == null) {
            return false;
        }
        players.remove(gamePlayer);
        gamePlayer.cancelRespawn();
        gamePlayer.setNonSpectator();
        module.log("Unregistered player " + player.getName() + " from active game " + module.index() + ".");
        for (Consumer<GamePlayer> action : unregistrationActions) {
            action.accept(gamePlayer);
        }
        return true;
    }

    public void activatePendingPlayers() {
        if (module.world() == null || pendingPlayers.isEmpty()) {
            return;
        }
        for (UUID uuid : Set.copyOf(pendingPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || playersById.containsKey(uuid)) {
                continue;
            }
            pendingPlayers.remove(uuid);
            registerNow(player);
        }
    }

    void registerPlayer(Player player) {
        register(player);
    }

    void unregisterPlayer(Player player) {
        unregister(player);
    }

    public GameModule module() {
        return module;
    }

    private void registerNow(Player player) {
        GamePlayer gamePlayer = new GamePlayer(this, module.world(), player.getUniqueId(), defaultSpawnLocation());
        players.add(gamePlayer);
        playersById.put(player.getUniqueId(), gamePlayer);
        module.log("Registering player " + player.getName() + " into game world " + module.world().bukkitWorld().getName()
            + " at " + formatLocation(gamePlayer.respawnLocation()) + ".");
        gamePlayer.teleport(gamePlayer.respawnLocation());
        for (Consumer<GamePlayer> action : registrationActions) {
            try {
                action.accept(gamePlayer);
            } catch (RuntimeException exception) {
                module.logError("Registration hook failed for player " + player.getName() + ".", exception);
            }
        }
    }

    private Collection<GamePlayer> filtered(boolean spectators) {
        Set<GamePlayer> filtered = new LinkedHashSet<>();
        for (GamePlayer player : players) {
            if (player.isSpectator() == spectators) {
                filtered.add(player);
            }
        }
        return Collections.unmodifiableSet(filtered);
    }

    private GameLocation defaultSpawnLocation() {
        return module.world().point("spawn") != null ? module.world().point("spawn") : module.world().spawnLocation();
    }

    private String formatLocation(GameLocation location) {
        return String.format("(%.2f, %.2f, %.2f)", location.x(), location.y(), location.z());
    }
}
