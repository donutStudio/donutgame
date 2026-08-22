package com.donutsforlife11.lavarun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

public class LavaRunPlayers {
    private final LavaRun game;

    public LavaRunPlayers(LavaRun game) {
        this.game = game;
    }

    public void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        int teamCount = (int) Math.ceil((double) players.size() / game.teamCount);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }
    public void setupPlayers() {
        for (GamePlayer player : game.playerManager().getPlayers()) {
            
        }
    }
}
