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
    private YamlConfiguration mapFile;
    private final Donutgame plugin;
    private String templateWorldName;
    private String instanceWorldName;
    private WorldManager worldManager;

    public GameMap(Donutgame plugin, File mapFile) {
        this.mapFile = YamlConfiguration.loadConfiguration(mapFile);
        this.plugin = plugin;
        this.worldManager = this.plugin.getWorldManager();
    }

    public CompletableFuture<GameMap> loadWorld() {
        ConfigurationSection world = mapFile.getConfigurationSection("world");

        if (world.getString("source").equals("filesystem")) {
            if (world.getString("format").equals("slime")) {
                templateWorldName = world.getString("id");
                instanceWorldName = templateWorldName + "_" + worldManager.incrementWorldIndex();

                return worldManager
                    .loadSlimeWorld(templateWorldName, instanceWorldName)
                    .thenApply(instance -> this);
            }
        }

        return CompletableFuture.failedFuture(new IllegalStateException("world didnt load idk"));
    }

    public void unloadWorld() {
        World world = Bukkit.getWorld(instanceWorldName);

        if (world == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        Bukkit.unloadWorld(world, false);
        worldManager.decrementWorldIndex();
    }

    public List<Location> getPoints(String pointType) {
        List<Location> locationList = new ArrayList<>();
        ConfigurationSection pointTypes = mapFile.getConfigurationSection("points");

        if (pointTypes == null) return locationList;
        
        List<?> objectList = pointTypes.getList(pointType);

        if (objectList != null) {
            for (Object obj : objectList) {
                if (obj instanceof Map) {
                    Map<?, ?> coords = (Map<?, ?>) obj;
                    try {
                        double x = Double.parseDouble(coords.get("x").toString());
                        double y = Double.parseDouble(coords.get("y").toString());
                        double z = Double.parseDouble(coords.get("z").toString());
                        
                        locationList.add(new Location(Bukkit.getWorld(instanceWorldName), x, y, z)); 
                    } catch (NullPointerException | NumberFormatException e) {
                        System.out.println("Invalid coordinate format under: " + pointType);
                    }
                }
            }
        }

        return locationList;
    }
}
