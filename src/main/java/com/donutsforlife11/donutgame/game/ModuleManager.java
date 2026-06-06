package com.donutsforlife11.donutgame.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.ModuleApi;
import com.donutsforlife11.donutgame.api.teams.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.player.GamePlayerManager;

public class ModuleManager {
    private final Donutgame plugin;
    private final Map<Integer, ActiveGame> activeGames = new HashMap<>();
    private final Map<UUID, Integer> playerGames = new HashMap<>();

    private int nextId = 0;
    private final Queue<Integer> freeIndexes = new LinkedList<>();

    public ModuleManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public LoadResult loadModule(GameModuleDescriptor descriptor, ConfigurationSection configOverrides, List<Player> initialPlayers) throws Exception {
        int id;

        if (!freeIndexes.isEmpty()) {
            id = freeIndexes.poll();
        } else {
            id = nextId++;
        }

        YamlConfiguration config = descriptor.createConfig();
        applyConfigOverrides(config, configOverrides);

        GamePlayerManager playerManager = new GamePlayerManager(plugin);
        ModuleApi context = new ModuleApi(
            plugin,
            id,
            descriptor.id(),
            config,
            new TeamManager(),
            new TimeManager(plugin),
            playerManager
        );
        GameModule module = descriptor.moduleClass().getDeclaredConstructor().newInstance();
        ActiveGame activeGame = new ActiveGame(module, context);

        activeGames.put(id, activeGame);

        RegistrationSummary initialRegistration = new RegistrationSummary(0, 0, 0, 0);

        try {
            module.onLoad(context);
            if (initialPlayers != null && !initialPlayers.isEmpty()) {
                initialRegistration = registerPlayers(id, initialPlayers, false);
            }
        } catch (Exception e) {
            activeGames.remove(id);
            context.shutdown();
            freeIndexes.offer(id);
            throw e;
        }

        return new LoadResult(id, initialRegistration);
    }

    public void unloadModule(int moduleIndex) {
        ActiveGame activeGame = activeGames.remove(moduleIndex);

        if (activeGame != null) {
            activeGame.module().onUnload();
            unregisterGamePlayers(moduleIndex);
            activeGame.context().shutdown();
            freeIndexes.offer(moduleIndex);
        }
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

    public void handlePlayerDeath(Player player) {
        ActiveGame activeGame = getActiveGame(player);

        if (activeGame != null) {
            activeGame.context().getInternalPlayerManager().handleDeath(player);
        }
    }

    public void handlePlayerKill(Player attacker, Player victim) {
        Integer attackerGame = playerGames.get(attacker.getUniqueId());
        Integer victimGame = playerGames.get(victim.getUniqueId());

        if (attackerGame == null || !attackerGame.equals(victimGame)) {
            return;
        }

        ActiveGame activeGame = activeGames.get(attackerGame);

        if (activeGame != null) {
            activeGame.context().getInternalPlayerManager().handleKill(attacker, victim);
        }
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
            }
        }

        return new RegistrationSummary(added, alreadyInGame, inOtherGame, players.size());
    }

    private ActiveGame getActiveGame(Player player) {
        Integer gameIndex = playerGames.get(player.getUniqueId());
        return gameIndex == null ? null : activeGames.get(gameIndex);
    }

    private void unregisterGamePlayers(int gameIndex) {
        playerGames.entrySet().removeIf(entry -> entry.getValue() == gameIndex);
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
