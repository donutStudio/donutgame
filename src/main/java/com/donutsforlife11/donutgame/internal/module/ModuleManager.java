package com.donutsforlife11.donutgame.internal.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.swing.UIManager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.internal.module.ModuleManager.LoadResult;

public class ModuleManager {
    private final Map<Integer, ActiveGame> activeGames = new HashMap<>();
    private final Map<UUID, Integer> playerGames = new HashMap<>();
    private final Queue<Integer> freeIndexes = new LinkedList<>();

    private int nextId = 0;

    public ModuleManager(Plugin plugin) {
        
    }

    public CompletableFuture<LoadResult> loadModule(GameModuleDescriptor descriptor, ConfigurationSection configOverrides, List<Player> initialPlayers) throws Exception {
        int id = allocateGameIndex();

        GameContext context = new GameContext();

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

            if (activeGame.context().playerManager().register(player, runCallbacks)) {
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

    public Map<Integer, GameModule> activeGames() {
        Map<Integer, GameModule> games = new HashMap<>();

        activeGames.forEach((index, activeGame) -> games.put(index, activeGame.module()));
        return Collections.unmodifiableMap(games);
    }
    public boolean hasActiveGame(int gameIndex) {
        return activeGames.containsKey(gameIndex);
    }

    private void unregisterGamePlayers(int gameIndex) {
        playerGames.entrySet().removeIf(entry -> entry.getValue() == gameIndex);
    }

    private int allocateGameIndex() {
        return freeIndexes.isEmpty() ? nextId++ : freeIndexes.poll();
    }
    private void initializeModule(GameModule module, GameContext context, GameModuleDescriptor descriptor) {
    }
    private void cleanupFailedLoad(int gameIndex, GameContext context) {
        activeGames.remove(gameIndex);
        context.shutdown().join();
        freeIndexes.offer(gameIndex);
    }
    private RegistrationSummary registerInitialPlayers(int gameIndex, List<Player> initialPlayers) {
        if (initialPlayers == null || initialPlayers.isEmpty()) {
            return new RegistrationSummary(0, 0, 0, 0);
        }

        return registerPlayers(gameIndex, initialPlayers, false);
    }

    private record ActiveGame(GameModule module, GameContext context) {
    }
    public record RegistrationSummary(int added, int alreadyInGame, int inOtherGames, int attempted) {
    }
    public record LoadResult(int gameIndex, RegistrationSummary registrationSummary) {
    }
}
