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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
    private final Set<GamePlayer> spectatablePlayers = new LinkedHashSet<>();
    private final Set<com.donutsforlife11.donutgame.api.team.GameTeam> spectatableTeams = new LinkedHashSet<>();
    private final Map<UUID, Set<GamePlayer>> spectatablePlayersByViewer = new LinkedHashMap<>();
    private final Map<UUID, Set<com.donutsforlife11.donutgame.api.team.GameTeam>> spectatableTeamsByViewer = new LinkedHashMap<>();

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

    public void setSpectatablePlayers(Collection<GamePlayer> players) {
        spectatablePlayers.clear();
        if (players == null) return;
        for (GamePlayer player : players) if (isRegistered(player)) spectatablePlayers.add(player);
    }

    public void setSpectatableTeams(Collection<com.donutsforlife11.donutgame.api.team.GameTeam> teams) {
        spectatableTeams.clear();
        if (teams == null) return;
        for (com.donutsforlife11.donutgame.api.team.GameTeam team : teams) {
            if (team != null && module.teamManager().getTeams().contains(team)) spectatableTeams.add(team);
        }
    }

    public Collection<GamePlayer> spectatablePlayers() {
        return Collections.unmodifiableSet(spectatablePlayers);
    }

    public Collection<com.donutsforlife11.donutgame.api.team.GameTeam> spectatableTeams() {
        return Collections.unmodifiableSet(spectatableTeams);
    }

    public boolean register(Player player) {
        Objects.requireNonNull(player);
        UUID uuid = player.getUniqueId();
        if (module.world() == null) {
            if (!pendingPlayers.add(uuid)) return false;
            module.log("Queued player " + player.getName() + " for registration until the game world is ready.");
            return true;
        }
        if (playersById.containsKey(uuid)) return false;
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
        if (gamePlayer == null) return false;
        players.remove(gamePlayer);
        spectatablePlayers.remove(gamePlayer);
        spectatablePlayersByViewer.remove(gamePlayer.uuid());
        spectatableTeamsByViewer.remove(gamePlayer.uuid());
        spectatablePlayersByViewer.values().forEach(targets -> targets.remove(gamePlayer));
        gamePlayer.cancelRespawn();
        gamePlayer.setNonSpectator();
        module.log("Unregistered player " + player.getName() + " from active game " + module.index() + ".");
        for (Consumer<GamePlayer> action : unregistrationActions) action.accept(gamePlayer);
        return true;
    }

    public void activatePendingPlayers() {
        if (module.world() == null || pendingPlayers.isEmpty()) {
            return;
        }
        for (UUID uuid : Set.copyOf(pendingPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || playersById.containsKey(uuid)) continue;
            pendingPlayers.remove(uuid);
            registerNow(player);
        }
    }

    public void activatePostStartPlayers() {
        activatePendingPlayers();
        for (GamePlayer player : players) {
            if (player.player() != null) {
                player.syncSpectatorState();
            }
        }
    }

    public void handlePlayerJoin(Player player) {
        GamePlayer gamePlayer = getPlayer(player);
        if (gamePlayer != null && gamePlayer.isSpectator()) {
            module.plugin().getServer().getScheduler().runTask(module.plugin(), gamePlayer::syncSpectatorState);
        }
    }

    public CompletableFuture<Void> evacuateForShutdown() {
        Location destination = shutdownDestination();
        List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (GamePlayer gamePlayer : List.copyOf(players)) {
            Player player = gamePlayer.player();
            gamePlayer.cancelRespawn();
            if (player == null) continue;
            player.closeInventory();
            gamePlayer.setNonSpectator();
            teleports.add(player.teleportAsync(destination).exceptionally(throwable -> false));
        }
        return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
    }

    public void clearForShutdown() {
        pendingPlayers.clear();
        players.clear();
        playersById.clear();
        spectatablePlayers.clear();
        spectatableTeams.clear();
        spectatablePlayersByViewer.clear();
        spectatableTeamsByViewer.clear();
    }

    public GameModule module() {
        return module;
    }

    public void setPlayerSpectatable(GamePlayer player, boolean spectatable) {
        if (player == null || !isRegistered(player)) return;
        if (spectatable) spectatablePlayers.add(player);
        else spectatablePlayers.remove(player);
    }

    public void setTeamSpectatable(com.donutsforlife11.donutgame.api.team.GameTeam team, boolean spectatable) {
        if (team == null || !module.teamManager().getTeams().contains(team)) return;
        if (spectatable) spectatableTeams.add(team);
        else spectatableTeams.remove(team);
    }

    public void removeSpectatableTeam(com.donutsforlife11.donutgame.api.team.GameTeam team) {
        spectatableTeams.remove(team);
        spectatableTeamsByViewer.values().forEach(targets -> targets.remove(team));
    }

    public void setSpectatablePlayers(GamePlayer viewer, Collection<GamePlayer> players) {
        if (viewer == null || !isRegistered(viewer)) return;
        Set<GamePlayer> targets = spectatablePlayersByViewer.computeIfAbsent(viewer.uuid(), ignored -> new LinkedHashSet<>());
        targets.clear();
        if (players == null) return;
        for (GamePlayer player : players) if (isRegistered(player)) targets.add(player);
    }

    public void setSpectatableTeams(GamePlayer viewer, Collection<com.donutsforlife11.donutgame.api.team.GameTeam> teams) {
        if (viewer == null || !isRegistered(viewer)) return;
        Set<com.donutsforlife11.donutgame.api.team.GameTeam> targets = spectatableTeamsByViewer.computeIfAbsent(viewer.uuid(), ignored -> new LinkedHashSet<>());
        targets.clear();
        if (teams == null) return;
        for (com.donutsforlife11.donutgame.api.team.GameTeam team : teams) {
            if (team != null && module.teamManager().getTeams().contains(team)) targets.add(team);
        }
    }

    public Collection<GamePlayer> spectatablePlayers(GamePlayer viewer) {
        if (viewer == null) return List.of();
        Set<GamePlayer> targets = spectatablePlayersByViewer.get(viewer.uuid());
        return targets == null ? spectatablePlayers() : Collections.unmodifiableSet(targets);
    }

    public Collection<com.donutsforlife11.donutgame.api.team.GameTeam> spectatableTeams(GamePlayer viewer) {
        if (viewer == null) return List.of();
        Set<com.donutsforlife11.donutgame.api.team.GameTeam> targets = spectatableTeamsByViewer.get(viewer.uuid());
        return targets == null ? spectatableTeams() : Collections.unmodifiableSet(targets);
    }

    private void registerNow(Player player) {
        GameLocation spawn = defaultSpawnLocation();
        GamePlayer gamePlayer = new GamePlayer(this, module.world(), player.getUniqueId(), spawn);
        players.add(gamePlayer);
        playersById.put(player.getUniqueId(), gamePlayer);
        module.log("Registering player " + player.getName() + " into game world " + module.world().bukkitWorld().getName() + " at " + formatLocation(spawn) + ".");
        gamePlayer.teleport(spawn);
        for (Consumer<GamePlayer> action : registrationActions) {
            try {
                action.accept(gamePlayer);
            } catch (RuntimeException exception) {
                module.logError("Registration hook failed for player " + player.getName() + ".", exception);
            }
        }
        if (module.hasStarted()) {
            gamePlayer.setSpectator(true, spawn);
        }
    }

    private Collection<GamePlayer> filtered(boolean spectators) {
        Set<GamePlayer> filtered = new LinkedHashSet<>();
        for (GamePlayer player : players) if (player.isSpectator() == spectators) filtered.add(player);
        return Collections.unmodifiableSet(filtered);
    }

    private GameLocation defaultSpawnLocation() {
        return module.world().getPoint("spawn") != null ? module.world().getPoint("spawn") : module.world().worldSpawn();
    }

    private Location shutdownDestination() {
        World currentWorld = module.world() == null ? null : module.world().bukkitWorld();
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(currentWorld)) return world.getSpawnLocation();
        }
        throw new IllegalStateException("Cannot unload game " + module.index() + " because no non-game destination world is loaded.");
    }

    private String formatLocation(GameLocation location) {
        return String.format("(%.2f, %.2f, %.2f)", location.x(), location.y(), location.z());
    }
}
