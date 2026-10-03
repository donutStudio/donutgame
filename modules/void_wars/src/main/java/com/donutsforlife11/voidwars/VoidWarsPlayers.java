package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.event.GamePlayerLateJoinEvent;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class VoidWarsPlayers {
    private final VoidWars game;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Set<UUID> lateSpectators = new LinkedHashSet<>();

    VoidWarsPlayers(VoidWars game) {
        this.game = game;
    }

    void assignTeams() {
        List<GamePlayer> players = new ArrayList<>(game.playerManager().getPlayers());
        Collections.shuffle(players);
        int teamCount = Math.max(1, (int) Math.ceil(players.size() / (double) game.teamSize));
        for (int i = 0; i < teamCount; i++) {
            game.teamManager().newColoredTeam();
        }
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % teams.size()).addPlayer(players.get(i));
        }
    }

    void assignNewPlayersToTeams() {
        List<GamePlayer> unteamed = new ArrayList<>(game.playerManager().getPlayers().stream()
            .filter(player -> !game.teamManager().playerHasTeam(player))
            .toList());
        if (unteamed.isEmpty()) {
            return;
        }
        Collections.shuffle(unteamed);
        for (GamePlayer player : unteamed) {
            GameTeam team = fairestTeamFor(player);
            team.addPlayer(player);
            lateSpectators.remove(player.uuid());
        }
    }

    void setupPlayer(GamePlayer player, boolean clearItems, boolean preserveLocation) {
        GameLocation spawn = game.world().getPoint(VoidWars.SPAWN);
        GameLocation startLocation = preserveLocation && player.location() != null ? player.location() : spawn;
        player.setSpawnPoint(startLocation);
        player.setSpectatablePlayers(game.teamSize <= 1 ? () -> game.playerManager().getPlayers() : List::of);
        player.setSpectatableTeams(game.teamSize <= 1 ? List::of : () -> game.teamManager().getTeams());
        if (lateSpectators.contains(player.uuid()) || game.roundStarted()) {
            player.setSpectator(true, startLocation);
            return;
        }
        player.setSpectator(false, startLocation);
        player.setGameMode(GameMode.SURVIVAL);
        player.heal();
        player.setHunger(20);
        player.setSaturation(20);
        player.setArrowsInBody(0);
        player.clearEffects();
        player.clearExperience();
        player.setLevel(99);
        player.setExp(0.99f);
        if (clearItems) {
            player.clearItems();
        }
    }

    int kills(GamePlayer player) {
        return kills.getOrDefault(player.uuid(), 0);
    }

    private boolean tryRespawn(GamePlayer player) {
        GameTeam team = player.getTeam();
        if (team == null) {
            return false;
        }
        if (livingTeammate(player) == null) {
            eliminateTeam(team);
            return false;
        }
        int respawnTicks = game.baseRespawnTicks * Math.max(0, team.getPlayers().size() - 1);
        player.respawn(respawnTicks, () -> {
            GamePlayer teammate = livingTeammate(player);
            return teammate == null ? player.spawnPoint() : teammate.location();
        });
        return true;
    }

    private void eliminateTeam(GameTeam team) {
        for (GamePlayer teammate : team.getPlayers()) {
            teammate.cancelRespawn();
        }
        game.uiManager().title(team.getPlayers(), Component.text(game.teamSize == 1 ? "Eliminated!" : "Team Eliminated!", NamedTextColor.RED, TextDecoration.BOLD));
    }

    void cancelRespawns() {
        for (GamePlayer player : game.playerManager().getPlayers()) {
            player.cancelRespawn();
        }
    }

    private GamePlayer livingTeammate(GamePlayer player) {
        GameTeam team = player.getTeam();
        if (team == null) {
            return null;
        }
        GamePlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        GameLocation location = player.location();
        for (GamePlayer teammate : team.getNonSpectators()) {
            if (teammate == player) {
                continue;
            }
            double distance = location == null || teammate.location() == null ? 0.0 : distanceSquared(location, teammate.location());
            if (distance < nearestDistance) {
                nearest = teammate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private double distanceSquared(GameLocation left, GameLocation right) {
        double x = left.x() - right.x();
        double y = left.y() - right.y();
        double z = left.z() - right.z();
        return x * x + y * y + z * z;
    }

    private void checkRoundOver() {
        checkRoundOver(null);
    }

    private void checkRoundOver(GamePlayer dyingPlayer) {
        if (!game.roundStarted()) {
            return;
        }
        Collection<GameTeam> aliveTeams = aliveTeams(dyingPlayer);
        if (aliveTeams.size() <= 1) {
            game.endRound(aliveTeams.isEmpty() ? List.of() : aliveTeams.iterator().next().getPlayers());
        }
    }

    private Collection<GameTeam> aliveTeams(GamePlayer dyingPlayer) {
        if (dyingPlayer == null) {
            return game.teamManager().getNonSpectatorTeams();
        }
        List<GameTeam> aliveTeams = new ArrayList<>();
        for (GameTeam team : game.teamManager().getTeams()) {
            boolean alive = team.getNonSpectators().stream().anyMatch(player -> player != dyingPlayer);
            if (alive) {
                aliveTeams.add(team);
            }
        }
        return aliveTeams;
    }

    @GameEventHandler
    public void onDeath(GameEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player == null) {
            return;
        }
        GamePlayer gameAttacker = creditedKiller(event.bukkitEvent());
        if (gameAttacker != null && gameAttacker != player) {
            kills.merge(gameAttacker.uuid(), 1, Integer::sum);
        }
        if (!game.roundStarted()) {
            player.respawn();
            return;
        }
        boolean canRespawn = livingTeammate(player) != null;
        if (canRespawn) {
            event.bukkitEvent().setKeepInventory(true);
            event.bukkitEvent().setKeepLevel(true);
            event.bukkitEvent().setDroppedExp(0);
            event.bukkitEvent().getDrops().clear();
        }
        GameLocation spectatorLocation = game.border() != null && player.location() != null && game.border().containsLocation(player.location())
            ? player.location()
            : game.world().getRegion(VoidWars.BORDER).center();
        player.setSpectator(true, spectatorLocation);
        if (canRespawn) {
            tryRespawn(player);
        } else {
            GameTeam team = player.getTeam();
            if (team != null) {
                eliminateTeam(team);
            }
        }
        checkRoundOver(player);
    }

    @GameEventHandler
    public void onJoin(GameEvent<PlayerJoinEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null && !game.teamManager().playerHasTeam(player)) {
            lateSpectators.add(player.uuid());
            setupPlayer(player, false, false);
        }
        checkRoundOver();
    }

    @GameEventHandler
    public void onLateJoin(GameEvent<GamePlayerLateJoinEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null && !game.teamManager().playerHasTeam(player)) {
            lateSpectators.add(player.uuid());
            event.bukkitEvent().setSpectatorLocation(game.world().getPoint(VoidWars.SPAWN));
        }
        checkRoundOver();
    }

    @GameEventHandler
    public void onQuit(GameEvent<PlayerQuitEvent> event) {
        GamePlayer player = event.get("player", GamePlayer.class);
        if (player != null) {
            player.setSpectator(true);
        }
        checkRoundOver();
    }

    private GamePlayer creditedKiller(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer == null && event.getDamageSource().getCausingEntity() instanceof Player causingPlayer) {
            killer = causingPlayer;
        }
        return killer == null ? null : game.playerManager().getPlayer(killer);
    }

    private GameTeam fairestTeamFor(GamePlayer player) {
        List<GameTeam> teams = new ArrayList<>(game.teamManager().getTeams());
        if (teams.isEmpty()) {
            return game.teamManager().newColoredTeam();
        }
        int minimumSize = teams.stream()
            .mapToInt(team -> team.getPlayers().size())
            .min()
            .orElse(0);
        GameTeam candidate = teams.stream()
            .filter(team -> team.getPlayers().size() == minimumSize && team.getPlayers().size() < game.teamSize)
            .findFirst()
            .orElse(null);
        if (candidate != null) {
            return candidate;
        }
        if (minimumSize >= game.teamSize) {
            return game.teamManager().newColoredTeam();
        }
        return teams.stream()
            .filter(team -> team.getPlayers().size() == minimumSize)
            .findFirst()
            .orElseGet(() -> game.teamManager().newColoredTeam());
    }
}
