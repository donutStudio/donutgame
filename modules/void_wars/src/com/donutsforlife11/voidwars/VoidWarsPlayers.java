package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;

import org.bukkit.GameMode;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.event.GamePlayerDeathEvent;
import com.donutsforlife11.donutgame.api.event.GamePlayerJoinEvent;
import com.donutsforlife11.donutgame.api.event.GamePlayerQuitEvent;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsPlayers {
    
    private final VoidWars game;

    private final Map<GamePlayer, Integer> playerKills = new HashMap<>();
    private final Set<UUID> forcedSpectators = new LinkedHashSet<>();

    public VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    public void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        int teamCount = (int) Math.ceil((double) players.size() / game.teamSize);
        Collections.shuffle(players);
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teamCount).addPlayer(players.get(i));
        }
    }

    public void setupPlayer(GamePlayer player, boolean clearItems) {
        GameLocation spawnPoint = game.world().getPoint(game.spawnPointName);
        player.teleport(spawnPoint);
        player.setSpawnPoint(spawnPoint);
        if (game.teamSize <= 1) {
            player.setSpectatablePlayers(() -> game.playerManager().getPlayers());
            player.setSpectatableTeams(List.of());
        } else {
            player.setSpectatablePlayers(List.of());
            player.setSpectatableTeams(() -> game.teamManager().getTeams());
        }
        if (isForcedSpectator(player) || game.roundStarted()) {
            player.setSpectator(true, spawnPoint);
            return;
        }
        player.heal();
        player.setHunger(20);
        player.setNonSpectator();
        player.setGameMode(GameMode.SURVIVAL);
        if (clearItems) {
            player.clearItems();
        }
        player.clearExperience();
        player.setLevel(99);
        player.setExp(0.99f);
        player.setSaturation(20);
        player.clearArrowsInBody();
    }

    public void forceSpectatorForGame(GamePlayer player) {
        if (player != null) {
            forcedSpectators.add(player.uuid());
        }
    }

    public boolean isForcedSpectator(GamePlayer player) {
        return player != null && forcedSpectators.contains(player.uuid());
    }

    public boolean tryRespawn(GamePlayer player) {
        GameTeam team = player.team();
        if (team == null) {
            return false;
        }
        int respawnTime = game.baseRespawnTime * (team.getMembers().size() - 1);
        if (team.allMembersSpectators()) {
            for (GamePlayer teammate : team.getMembers()) {
                teammate.cancelRespawn();
            }
            if (game.teamSize == 1) {
                game.uiManager().title(team.getMembers(), Component.text("Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
            } else {
                game.uiManager().title(team.getMembers(), Component.text("Team Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
            }
            return false;
        } else {
            player.respawn(respawnTime, () -> {
                GamePlayer nearestTeammate = getNearestTeammate(player);
                return nearestTeammate == null ? player.spawnPoint() : nearestTeammate.location();
            });
            return true;
        }
    }

    public GamePlayer getNearestTeammate(GamePlayer player) {
        double distance = Double.MAX_VALUE;
        GamePlayer nearestTeammate = null;
        for (GamePlayer teammate : player.team().getNonSpectatorMembers()) {
            if (teammate == player) {
                continue;
            }
            double teammateDistance = player.location().distanceSquared(teammate.location());
            if (teammateDistance < distance) {
                distance = teammateDistance;
                nearestTeammate = teammate;
            }
        }
        return nearestTeammate;
    }

    public int getPlayerKills(GamePlayer player) {
        return playerKills.getOrDefault(player, 0);
    }

    @GameEventHandler
    public void onPlayerDeath(GamePlayerDeathEvent event) {
        GamePlayer player = event.getPlayer();
        GameEntity damager = event.getDamager();
        if (damager instanceof GamePlayer attacker && attacker != player) {
            playerKills.put(attacker, playerKills.getOrDefault(attacker, 0) + 1);
        }
        if (game.roundStarted()) {
            boolean canRespawn = hasLivingTeammate(player);
            GameLocation spectatorLocation = game.world().posInRegion(player.location(), game.borderRegionName)
                ? player.location()
                : game.world().getRegion(game.borderRegionName).center();
            if (canRespawn) {
                event.setDropItems(false);
                event.clearDrops();
                event.setDroppedExp(0);
                event.setKeepLevel(true);
                event.setKeepInventory(true);
            }
            player.setSpectator(true, spectatorLocation);
            event.setRespawnLocation(spectatorLocation);
            tryRespawn(player);
        } else {
            player.respawn();
        }
        checkGameOver();
    }
    @GameEventHandler
    public void onPlayerJoin(GamePlayerJoinEvent event) {
        if (!game.teamManager().playerHasTeam(event.getPlayer())) {
            forceSpectatorForGame(event.getPlayer());
        }
        event.getPlayer().setSpectator(true);
        checkGameOver();
    }
    @GameEventHandler
    public void onPlayerQuit(GamePlayerQuitEvent event) {
        event.getPlayer().setSpectator(true);
        checkGameOver();
    }

    public void checkGameOver() {
        if (!game.roundStarted()) {
            return;
        }
        Collection<GameTeam> aliveTeams = game.teamManager().getNonSpectatorTeams();
        if (aliveTeams.size() > 1) {
            return;
        }
        game.endRound(aliveTeams.isEmpty() ? Collections.emptyList() : aliveTeams.iterator().next().getMembers());
    }

    private boolean hasLivingTeammate(GamePlayer player) {
        GameTeam team = player.team();
        if (team == null) {
            return false;
        }
        for (GamePlayer teammate : team.getNonSpectatorMembers()) {
            if (teammate != player) {
                return true;
            }
        }
        return false;
    }
}
