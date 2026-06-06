package com.donutsforlife11.donutgame.game;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.util.WorldManager;

public class GameMap {
    private final YamlConfiguration mapFile;
    private final Donutgame plugin;
    private final WorldManager worldManager;
    private String templateWorldName;
    private WorldManager.WorldSession activeSession;

    public GameMap(Donutgame plugin, File mapFile) {
        this.mapFile = YamlConfiguration.loadConfiguration(mapFile);
        this.plugin = plugin;
        this.worldManager = this.plugin.getWorldManager();
    }

    public CompletableFuture<GameMap> loadWorld() {
        if (activeSession != null) {
            return CompletableFuture.completedFuture(this);
        }

        ConfigurationSection world = mapFile.getConfigurationSection("world");

        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Map is missing a world section."));
        }

        if (world.getString("source").equals("filesystem")) {
            if (world.getString("format").equals("slime")) {
                templateWorldName = world.getString("id");

                return worldManager
                    .loadSlimeWorld(templateWorldName)
                    .thenApply(session -> {
                        activeSession = session;
                        return this;
                    });
            }
        }

        return CompletableFuture.failedFuture(new IllegalStateException("world didnt load idk"));
    }

    public CompletableFuture<GameMap> resetWorld() {
        unloadWorld();
        return loadWorld();
    }

    public void unloadWorld() {
        if (activeSession == null) {
            return;
        }

        String instanceWorldName = activeSession.instanceWorldName();
        World world = Bukkit.getWorld(instanceWorldName);

        if (world != null) {
            for (Player player : world.getPlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            Bukkit.unloadWorld(world, false);
        }

        WorldManager.WorldSession releasedSession = worldManager.releaseWorld(instanceWorldName);
        if (releasedSession != null) {
            plugin.getPlayerStateStore().clearWorld(releasedSession.playerStateId());
        }

        activeSession = null;
    }

    public List<Location> getPoints(String pointType) {
        List<Location> locationList = new ArrayList<>();
        ConfigurationSection pointTypes = mapFile.getConfigurationSection("points");
        World world = getWorld();

        if (pointTypes == null || world == null) {
            return locationList;
        }
        
        List<?> objectList = pointTypes.getList(pointType);

        if (objectList != null) {
            for (Object obj : objectList) {
                if (obj instanceof Map) {
                    Map<?, ?> coords = (Map<?, ?>) obj;
                    try {
                        double x = Double.parseDouble(coords.get("x").toString());
                        double y = Double.parseDouble(coords.get("y").toString());
                        double z = Double.parseDouble(coords.get("z").toString());
                        
                        locationList.add(new Location(world, x, y, z)); 
                    } catch (NullPointerException | NumberFormatException e) {
                        System.out.println("Invalid coordinate format under: " + pointType);
                    }
                }
            }
        }

        return locationList;
    }

    public World getWorld() {
        return activeSession == null ? null : Bukkit.getWorld(activeSession.instanceWorldName());
    }

    public String getInstanceWorldName() {
        return activeSession == null ? null : activeSession.instanceWorldName();
    }

    public String getTemplateWorldName() {
        return templateWorldName;
    }

    public boolean isLoaded() {
        return activeSession != null;
    }
}
