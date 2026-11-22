package com.donutsforlife11.zonewars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.api.item.GameItem;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.object.GameBlockType;
import com.donutsforlife11.donutgame.api.object.GameBlocks;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.format.NamedTextColor;

public class ZoneWarsPlayers {
    private ZoneWars game;
    private final Map<GameTeam, GameLocation> teamSpawns = new HashMap<>();
    private final Map<NamedTextColor, GameBlockType> colorMap = new HashMap<>(Map.ofEntries(
        Map.entry(NamedTextColor.BLACK, GameBlocks.BLACK_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_BLUE, GameBlocks.BLUE_STAINED_GLASS),
        Map.entry(NamedTextColor.BLUE, GameBlocks.BLUE_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_GREEN, GameBlocks.GREEN_STAINED_GLASS),
        Map.entry(NamedTextColor.GREEN, GameBlocks.LIME_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_AQUA, GameBlocks.CYAN_STAINED_GLASS),
        Map.entry(NamedTextColor.AQUA, GameBlocks.LIGHT_BLUE_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_RED, GameBlocks.BROWN_STAINED_GLASS),
        Map.entry(NamedTextColor.RED, GameBlocks.RED_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_PURPLE, GameBlocks.PURPLE_STAINED_GLASS),
        Map.entry(NamedTextColor.LIGHT_PURPLE, GameBlocks.PINK_STAINED_GLASS),
        Map.entry(NamedTextColor.GOLD, GameBlocks.ORANGE_STAINED_GLASS),
        Map.entry(NamedTextColor.YELLOW, GameBlocks.YELLOW_STAINED_GLASS),
        Map.entry(NamedTextColor.DARK_GRAY, GameBlocks.GRAY_STAINED_GLASS),
        Map.entry(NamedTextColor.GRAY, GameBlocks.LIGHT_GRAY_STAINED_GLASS),
        Map.entry(NamedTextColor.WHITE, GameBlocks.WHITE_STAINED_GLASS)
    ));
    private final List<GameRegion> spawnRegions = new ArrayList<>();

    public ZoneWarsPlayers(ZoneWars game) {
        this.game = game;
    }

    public void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        Collections.shuffle(players);
        for (int i = 0; i < game.teamCount(); i++) {
            game.teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            GamePlayer player = players.get(i);
            teams.get(i % game.teamCount()).addPlayer(player);
        }
    }

    public void loadPlayers() {
        Map<Integer, GameItem> kit = loadKit();
        if (game.teamManager().getTeams().isEmpty()) assignTeams();
        int targetSpawn = 0;
        for (GameTeam team : game.teamManager().getTeams()) {
            for (GamePlayer player : team.getPlayers()) {
                GameLocation spawn = game.spawns().get(targetSpawn);
                spawn = new GameLocation(spawn.blockX() + 0.5d, spawn.y(), spawn.blockZ() + 0.5d);
                player.teleport(spawn);
                player.setSpawnPoint(spawn);
                player.clearItems();
                kit.forEach(player::setInventoryItem);
                teamSpawns.put(team, spawn);
            }
            targetSpawn = (targetSpawn + 1) % game.spawns().size();
        }
        placeSpawns();
    }
    public void startPlayers() {
        for (GameRegion region : spawnRegions) {
            game.world().fill(region, GameBlocks.AIR);
        }
    }

    private Map<Integer, GameItem> loadKit() {
        YamlConfiguration config = game.data().config("kit.yml");
        Map<Integer, GameItem> items = new LinkedHashMap<>();
        readKitSlots(items, config.getConfigurationSection("kit.hotbar"), 0, 9);
        readKitSlots(items, config.getConfigurationSection("kit.inventory"), 9, 27);
        Map<String, Integer> armorSlots = Map.of("feet", 36, "legs", 37, "chest", 38, "head", 39);
        for (String armor : List.of("feet", "legs", "chest", "head")) {
            String item = config.getString("kit.armor." + armor);
            if (item != null) items.put(armorSlots.get(armor), game.data().item(item));
        }
        String offhand = config.getString("kit.weapon.offhand");
        if (offhand != null) items.put(40, game.data().item(offhand));
        return items;
    }

    private void readKitSlots(Map<Integer, GameItem> items, ConfigurationSection section, int offset, int size) {
        if (section == null) return;
        for (String key : section.getKeys(false).stream().sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList()) {
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= size) throw new IllegalArgumentException("Invalid kit slot " + section.getCurrentPath() + "." + key);
            items.put(offset + slot, game.data().item(section.getString(key)));
        }
    }

    public void placeSpawns() {
        Map<GameLocation, Set<GameTeam>> spawnTeams = new HashMap<>();
        for (Map.Entry<GameTeam, GameLocation> entry : teamSpawns.entrySet()) {
            spawnTeams.computeIfAbsent(entry.getValue(), k -> new HashSet<>())
                    .add(entry.getKey());
        }
        // Calculate an integer block midpoint for all points to face toward
        GameRegion borderRegion = game.borderRegion();
        GameLocation center = new GameLocation(
            (borderRegion.min().x() + borderRegion.max().x()) / 2d,
            (borderRegion.min().y() + borderRegion.max().y()) / 2d,
            (borderRegion.min().z() + borderRegion.max().z()) / 2d
        );
        center = new GameLocation(center.blockX(), center.blockY(), center.blockZ());
        for (GameLocation point : spawnTeams.keySet()) {
            double xDistance = Math.abs(center.x() - point.x());
            double zDistance = Math.abs(center.z() - point.z());
            GameRegion fillRegion;
            if (xDistance > zDistance) {
                fillRegion = new GameRegion(
                    new GameLocation(point.x() - 1, point.y(), point.z() - 3), 
                    new GameLocation(point.x() + 1, point.y() + 2, point.z() + 3)
                );
            } else {
                fillRegion = new GameRegion(
                    new GameLocation(point.x() - 3, point.y(), point.z() - 1), 
                    new GameLocation(point.x() + 3, point.y() + 2, point.z() + 1)
                );
            }
            spawnRegions.add(fillRegion);
            if (spawnTeams.get(point).size() > 1) {
                game.world().replace(fillRegion, GameBlocks.AIR, GameBlocks.WHITE_STAINED_GLASS);
            } else {
                NamedTextColor color = (new ArrayList<>(spawnTeams.get(point))).getFirst().color();
                GameBlockType block = colorMap.getOrDefault(color, GameBlocks.WHITE_STAINED_GLASS);
                game.world().replace(fillRegion, GameBlocks.AIR, block);
            }
            GameRegion clearRegion = new GameRegion(
                new GameLocation(fillRegion.min().x() + 1, fillRegion.min().y(), fillRegion.min().z() + 1),
                new GameLocation(fillRegion.max().x() - 1, fillRegion.max().y() - 1, fillRegion.max().z() - 1)
            );
            game.world().fill(clearRegion, GameBlocks.AIR);
        }
    }
}
