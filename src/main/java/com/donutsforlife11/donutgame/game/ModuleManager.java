package com.donutsforlife11.donutgame.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.ModuleApi;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

public class ModuleManager {
    private final Donutgame plugin;
    private final Map<Integer, ActiveGame> activeGames = new HashMap<>();
    private final Map<UUID, Integer> playerGames = new HashMap<>();

    private int nextId = 0;
    private final Queue<Integer> freeIndexes = new LinkedList<>();

    public ModuleManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<LoadResult> loadModule(GameModuleDescriptor descriptor, ConfigurationSection configOverrides, List<Player> initialPlayers) throws Exception {
        int id = allocateGameIndex();

        YamlConfiguration config = descriptor.createConfig();
        applyConfigOverrides(config, configOverrides);

        PlayerManager playerManager = new PlayerManager(plugin);
        UIManager uiManager = new UIManager(plugin);

        ModuleApi context = new ModuleApi(
            plugin,
            id,
            descriptor.id(),
            config,
            new TeamManager(),
            new TimeManager(plugin),
            playerManager,
            uiManager
        );

        GameModule module = descriptor.moduleClass().getDeclaredConstructor().newInstance();
        ActiveGame activeGame = new ActiveGame(module, context);

        activeGames.put(id, activeGame);

        try {
            initializeModule(module, context, descriptor);
            RegistrationSummary registration = registerInitialPlayers(id, initialPlayers);
            context.registerEvents(module);

            return module.startLoadSequence().thenApply(ignored -> new LoadResult(id, registration)).whenComplete((result, error) -> {
                if (error != null) {
                    cleanupFailedLoad(id, context);
                }
            });
        } catch (Exception e) {
            cleanupFailedLoad(id, context);
            throw e;
        }
    }

    public CompletableFuture<Void> unloadModule(int moduleIndex) {
        ActiveGame activeGame = activeGames.remove(moduleIndex);

        if (activeGame == null) {
            return CompletableFuture.completedFuture(null);
        }

        activeGame.module().onUnload();
        unregisterGamePlayers(moduleIndex);

        return activeGame.context().shutdown().whenComplete((ignored, error) -> freeIndexes.offer(moduleIndex));
    }

    public Map<Integer, GameModule> getActiveGames() {
        Map<Integer, GameModule> games = new HashMap<>();

        activeGames.forEach((index, activeGame) -> games.put(index, activeGame.module()));
        return Collections.unmodifiableMap(games);
    }

    public boolean hasActiveGame(int gameIndex) {
        return activeGames.containsKey(gameIndex);
    }

    public RegistrationSummary registerPlayers(int gameIndex, List<Player> players) {
        return registerPlayers(gameIndex, players, true);
    }

    public void handlePlayerDisconnect(Player player) {
        ActiveGame activeGame = getActiveGame(player);

        if (activeGame == null) {
            return;
        }

        activeGame.context().getInternalPlayerManager().handleDisconnect(player);
        playerGames.remove(player.getUniqueId());
    }

    public void handlePlayerRespawn(Player player, PlayerRespawnEvent event) {
        ActiveGame activeGame = getActiveGame(player);

        if (activeGame != null) {
            activeGame.context().getInternalPlayerManager().applyRespawnLocation(player, event);
        }
    }

    public void handlePlayerWorldChange(Player player, org.bukkit.World toWorld) {
        ActiveGame activeGame = getActiveGame(player);

        if (activeGame == null || activeGame.module().world == null) {
            return;
        }

        if (activeGame.module().world.getBukkitWorld().equals(toWorld)) {
            activeGame.context().getInternalPlayerManager().notifyPlayerEnteredWorld(player);
            return;
        }

        activeGame.context().getInternalPlayerManager().notifyPlayerLeftWorld(player);
    }

    private RegistrationSummary registerPlayers(int gameIndex, List<Player> players, boolean runCallbacks) {
        ActiveGame activeGame = activeGames.get(gameIndex);

        if (activeGame == null) {
            return new RegistrationSummary(0, 0, 0, 0);
        }

        int added = 0;
        int alreadyInGame = 0;
        int inOtherGame = 0;

        for (Player player : players) {
            Integer currentGame = playerGames.get(player.getUniqueId());

            if (currentGame != null) {
                if (currentGame == gameIndex) {
                    alreadyInGame++;
                } else {
                    inOtherGame++;
                }
                continue;
            }

            if (activeGame.context().getInternalPlayerManager().register(player, runCallbacks)) {
                playerGames.put(player.getUniqueId(), gameIndex);
                added++;

                GameWorld gameWorld = activeGame.module().world;
                if (gameWorld != null) {
                    activeGame.context().mapManager().teleportPlayerToSpawn(player).exceptionally(error -> {
                        logLifecycleFailure("Failed to teleport player " + player.getName() + " into game " + gameIndex, error);
                        return false;
                    });
                }
            }
        }

        return new RegistrationSummary(added, alreadyInGame, inOtherGame, players.size());
    }

    private ActiveGame getActiveGame(Player player) {
        Integer gameIndex = playerGames.get(player.getUniqueId());
        return gameIndex == null ? null : activeGames.get(gameIndex);
    }

    private int allocateGameIndex() {
        return freeIndexes.isEmpty() ? nextId++ : freeIndexes.poll();
    }

    private void initializeModule(GameModule module, ModuleApi context, GameModuleDescriptor descriptor) {
        module.context = context;
        module.mapManager = context.mapManager();
        module.playerManager = context.playerManager();
        module.timeManager = context.timeManager();
        module.uiManager = context.uiManager();
        module.teamManager = context.teamManager();
        module.borderManager = context.borderManager();
        module.config = context.getConfig();
        module.mapManager.onMapChanged((map, world) -> {
            module.map = map;
            module.world = world;
        });

        module.gameId = descriptor.id();
        module.setGameName(descriptor.name());
    }

    private RegistrationSummary registerInitialPlayers(int gameIndex, List<Player> initialPlayers) {
        if (initialPlayers == null || initialPlayers.isEmpty()) {
            return new RegistrationSummary(0, 0, 0, 0);
        }

        return registerPlayers(gameIndex, initialPlayers, false);
    }
    private void unregisterGamePlayers(int gameIndex) {
        playerGames.entrySet().removeIf(entry -> entry.getValue() == gameIndex);
    }

    private void cleanupFailedLoad(int gameIndex, ModuleApi context) {
        activeGames.remove(gameIndex);
        context.shutdown().join();
        freeIndexes.offer(gameIndex);
    }

    private void logLifecycleFailure(String message, Throwable error) {
        plugin.getLogger().severe(message);
        error.printStackTrace();
    }

    private void applyConfigOverrides(YamlConfiguration config, ConfigurationSection configOverrides) {
        if (configOverrides == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : configOverrides.getValues(true).entrySet()) {
            String key = entry.getKey();

            if ("id".equals(key) || "main_class".equals(key)) {
                continue;
            }

            config.set(key, entry.getValue());
        }
    }

    private record ActiveGame(GameModule module, ModuleApi context) {
    }

    public record RegistrationSummary(int added, int alreadyInGame, int inOtherGames, int attempted) {
    }

    public record LoadResult(int gameIndex, RegistrationSummary registrationSummary) {
    }
}
