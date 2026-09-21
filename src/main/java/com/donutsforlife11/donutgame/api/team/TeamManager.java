package com.donutsforlife11.donutgame.api.team;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scoreboard.Scoreboard;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

import net.kyori.adventure.text.format.NamedTextColor;

public class TeamManager {
    private static final List<NamedTextColor> COLORS = List.of(
        NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW,
        NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.AQUA, NamedTextColor.DARK_GREEN,
        NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_RED, NamedTextColor.DARK_AQUA, NamedTextColor.DARK_BLUE,
        NamedTextColor.GRAY, NamedTextColor.DARK_GRAY, NamedTextColor.BLACK, NamedTextColor.WHITE
    );

    private final PlayerManager playerManager;
    private final Scoreboard scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
    private final Set<GameTeam> teams = new LinkedHashSet<>();
    private final Map<UUID, GameTeam> teamsByPlayer = new LinkedHashMap<>();
    private int colorIndex;

    public TeamManager(PlayerManager playerManager) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
    }

    public void initialize() {
        registerFriendlyFireListener();
    }

    public GameTeam newTeam() {
        GameTeam team = new GameTeam(this, scoreboard, teams.size());
        teams.add(team);
        return team;
    }

    public GameTeam newColoredTeam() {
        return newTeam().setColor(COLORS.get(colorIndex++ % COLORS.size()));
    }

    public Collection<GameTeam> getTeams() {
        return Collections.unmodifiableSet(teams);
    }

    public Collection<GameTeam> getSpectatorTeams() {
        return filteredTeams(true);
    }

    public Collection<GameTeam> getNonSpectatorTeams() {
        return filteredTeams(false);
    }

    public GameTeam getPlayerTeam(GamePlayer player) {
        return player == null ? null : teamsByPlayer.get(player.uuid());
    }

    public boolean playerHasTeam(GamePlayer player) {
        return getPlayerTeam(player) != null;
    }

    public Scoreboard scoreboard() {
        return scoreboard;
    }

    public void clear() {
        for (GameTeam team : Set.copyOf(teams)) {
            team.remove();
        }
        teamsByPlayer.clear();
        colorIndex = 0;
    }

    public void syncPlayer(GamePlayer player) {
        GameTeam team = getPlayerTeam(player);
        if (team != null) {
            team.syncPlayer(player);
        }
    }

    Collection<GameTeam> teamsModifiable() {
        return teams;
    }

    PlayerManager playerManager() {
        return playerManager;
    }

    void assign(GameTeam team, GamePlayer player) {
        GameTeam current = getPlayerTeam(player);
        if (current != null && current != team) {
            current.removePlayer(player);
        }
        teamsByPlayer.put(player.uuid(), team);
    }

    void unassign(GameTeam team, GamePlayer player) {
        teamsByPlayer.remove(player.uuid(), team);
    }

    private Collection<GameTeam> filteredTeams(boolean spectators) {
        Set<GameTeam> matches = new LinkedHashSet<>();
        for (GameTeam team : teams) {
            if (team.allSpectators() == spectators) {
                matches.add(team);
            }
        }
        return Collections.unmodifiableSet(matches);
    }

    private void registerFriendlyFireListener() {
        playerManager.module().registerDynamicEvent(
            new Listener() {},
            EntityDamageEvent.class,
            (listener, event) -> handleFriendlyFire((EntityDamageEvent) event),
            EventPriority.HIGHEST,
            true
        );
    }

    private void handleFriendlyFire(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player attacker = attackingPlayer(event);
        if (attacker == null || attacker == target) {
            return;
        }
        GamePlayer targetPlayer = playerManager.getPlayer(target);
        GamePlayer attackerPlayer = playerManager.getPlayer(attacker);
        GameTeam team = getPlayerTeam(targetPlayer);
        if (team != null && team == getPlayerTeam(attackerPlayer) && !team.allowsFriendlyFireDamage(event.getDamageSource().getDamageType())) {
            event.setCancelled(true);
        }
    }

    private Player attackingPlayer(EntityDamageEvent event) {
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }
        Entity directEntity = event.getDamageSource().getDirectEntity();
        if (directEntity instanceof Player player) {
            return player;
        }
        if (directEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
