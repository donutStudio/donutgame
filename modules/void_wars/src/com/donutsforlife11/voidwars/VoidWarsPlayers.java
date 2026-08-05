package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.team.GameTeam;

public class VoidWarsPlayers {
    private final VoidWars game;

    public VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    public void assignTeams(int teamSize) {
        if (teamSize <= 0) {
            throw new IllegalArgumentException("Team size must be greater than zero!");
        }
        List<Player> players = new ArrayList<>(game.playerManager().getNonSpectators());
        int teamCount = (int) Math.ceil((double) players.size() / teamSize);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }
    public void setupPlayer(Player player) {
        Location spawnPoint = game.world().getPoints("spawn").get(0);
        game.playerManager().setPlayerSpawn(player, spawnPoint);
        player.teleportAsync(spawnPoint);
        if (game.started()) {
            game.playerManager().setSpectator(player);
        } else {
            game.playerManager().setNonSpectator(player);
            player.setGameMode(GameMode.ADVENTURE);
        }
    }
}
