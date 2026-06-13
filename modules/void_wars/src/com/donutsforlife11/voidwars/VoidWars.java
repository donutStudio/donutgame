package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.teams.GameTeam;
import com.donutsforlife11.donutgame.game.GameContext;
import com.donutsforlife11.donutgame.game.GameModule;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public class VoidWars extends GameModule {
    public boolean gameStarted = false;

    @Override
    public void beforeLoad(GameContext context) {
        mapManager.setMap(GameMap.fromPath("void_wars/classic_enhanced.dmap"));
    }

    @Override
    public void onLoad(GameContext context) {
        playerManager.onPlayerEnteredWorld(player -> {
            setupPlayer(player);
        });

        registerEvents(new VoidWarsEvents(this));
        assignTeams(config.getInt("team_size"));
    }

    @Override
    public void onStart() {
        gameStarted = true;
        for (Player player : playerManager.getNonSpectators()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        timeManager.formattedCountdown(config.getInt("ground_collapse_time") * 20, formattedNumber -> {
            uiManager.actionbar(Audience.audience(playerManager.getPlayers()), Component.text("Ground collapses in ").append(formattedNumber));
        }).onEnd(timer -> {
            for (BoundingBox box : world.getRegions("spawn_platform")) {
                world.fillArea(box, Material.AIR);
            }
        }).start();

        GameBorder testBorder = borderManager.createBorder(BorderShape.CUBOID, new Location(world.getBukkitWorld(), 0, 0, 0), new Vector(70, 70, 70));
        timeManager.createTimer(500).onEnd(timer -> {
            testBorder.setDimensions(new Vector(20, 20, 20), 400);
            timeManager.createTimer(100).onEnd(timer2 -> {
                testBorder.setCenter(new Location(world.getBukkitWorld(), 7, 3, 5), 100);
                testBorder.setDimensions(new Vector(20, 5, 30));
            }).start();
        }).start();
    }

    @Override
    public void onUnload() {
        context.getLogger().info("ruhas gnut gnut gnut");
    }

    public boolean gameStarted() {
        return gameStarted;
    }

    private void assignTeams(int teamSize) {
        if (teamSize <= 0) {
            throw new IllegalArgumentException("Team size must be greater than zero!");
        }
        List<Player> players = new ArrayList<>(playerManager.getNonSpectators());
        int teamCount = (int) Math.ceil((double) players.size() / teamSize);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            teamManager.createTeam(gameId + "." + i);
        }
        List<GameTeam> teams = new ArrayList<>(teamManager.getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }

    private void setupPlayer(Player player) {
        Location spawnPoint = world.getPoints("spawn").getFirst();
        player.teleportAsync(spawnPoint);
        playerManager.setPlayerSpawn(player, spawnPoint);
        if (gameStarted) {
            playerManager.setSpectator(player);
        } else {
            playerManager.setNonSpectator(player);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }
}
