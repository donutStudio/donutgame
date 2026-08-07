package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsPlayers implements Listener {
    private final VoidWars game;

    public VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    public void assignTeams(int teamSize) {
        if (teamSize <= 0) {
            throw new IllegalArgumentException("Team size must be greater than zero!");
        }
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getNonSpectators());
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

    public void setupPlayer(GamePlayer player) {
        GameLocation spawnPoint = game.world().getPoints("spawn").get(0);
        player.setRespawnLocation(spawnPoint);
        player.teleport(spawnPoint);
        if (game.started()) {
            player.setSpectator();
        } else {
            player.setNonSpectator();
            if (player.player() != null) {
                player.player().setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GamePlayer player = game.playerManager().getPlayer(event.getPlayer());
        if (player == null) {
            return;
        }
        if (game.started()) {
            if (tryRespawn(player, game.config().getInt("base_respawn_time"))) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
        } else {
            player.respawn();
        }
    }

    public boolean tryRespawn(GamePlayer player, int baseRespawnTime) {
        GameTeam team = game.teamManager().getPlayerTeam(player);
        int respawnTime = baseRespawnTime * (team.getMembers().size() - 1);

        if (isTeamEliminated(team)) {
            for (GamePlayer teammate : team.getMembers()) {
                teammate.cancelRespawn();
                if (game.config().getInt("team_size") == 1) {
                    game.uiManager().title(teammate, Component.text("Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                } else {
                    game.uiManager().title(teammate, Component.text("Team Eliminated!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                }
            }
        } else {
            player.respawn(respawnTime * 20);
        }
        return !isTeamEliminated(team);
    }

    public boolean isTeamEliminated(GameTeam team) {
        for (GamePlayer player : team.getMembers()) {
            if (!player.isSpectator()) {
                return false;
            }
        }
        return true;
    }

    public int alivePlayerCount() {
        return game.playerManager().getNonSpectators().size();
    }

    public int aliveTeamCount() {
        int aliveTeamCount = 0;
        for (GameTeam team : game.teamManager().getTeams()) {
            if (!isTeamEliminated(team)) {
                aliveTeamCount += 1;
            }
        }
        return aliveTeamCount;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        GamePlayer player = game.playerManager().getPlayer(event.getPlayer());
        if (player == null) {
            return;
        }
        if (!game.started()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        GamePlayer player = game.playerManager().getPlayer(event.getPlayer());
        if (player == null) {
            return;
        }
        if (game.started()) {
            tryRespawn(player, game.config().getInt("base_respawn_time"));
        }
    }
}
