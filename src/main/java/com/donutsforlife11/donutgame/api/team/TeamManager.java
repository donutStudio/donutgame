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
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scoreboard.Scoreboard;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.PlayerManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class TeamManager {
    private static final List<ColoredTeamPreset> COLORED_TEAM_PRESETS = List.of(
        new ColoredTeamPreset(NamedTextColor.RED, "Red"),
        new ColoredTeamPreset(NamedTextColor.BLUE, "Blue"),
        new ColoredTeamPreset(NamedTextColor.GREEN, "Green"),
        new ColoredTeamPreset(NamedTextColor.YELLOW, "Yellow"),
        new ColoredTeamPreset(NamedTextColor.LIGHT_PURPLE, "Pink"),
        new ColoredTeamPreset(NamedTextColor.GOLD, "Gold"),
        new ColoredTeamPreset(NamedTextColor.AQUA, "Aqua"),
        new ColoredTeamPreset(NamedTextColor.DARK_GREEN, "Dark Green"),
        new ColoredTeamPreset(NamedTextColor.DARK_PURPLE, "Purple"),
        new ColoredTeamPreset(NamedTextColor.DARK_RED, "Dark Red"),
        new ColoredTeamPreset(NamedTextColor.DARK_AQUA, "Dark Aqua"),
        new ColoredTeamPreset(NamedTextColor.DARK_BLUE, "Dark Blue"),
        new ColoredTeamPreset(NamedTextColor.GRAY, "Gray"),
        new ColoredTeamPreset(NamedTextColor.DARK_GRAY, "Dark Gray"),
        new ColoredTeamPreset(NamedTextColor.BLACK, "Black"),
        new ColoredTeamPreset(NamedTextColor.WHITE, "White")
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
        int index = colorIndex++;
        ColoredTeamPreset preset = COLORED_TEAM_PRESETS.get(index % COLORED_TEAM_PRESETS.size());
        GameTeam team = newTeam().setColor(preset.color());
        Material dyeMaterial = Material.matchMaterial(GameItemComponents.teamDyeColor(team).name() + "_DYE");
        if (dyeMaterial != null) {
            team.setItem(GameItem.of(dyeMaterial));
        }
        if (index < COLORED_TEAM_PRESETS.size()) {
            team.setDisplayName(Component.text(preset.label() + " Team", preset.color()));
            team.setPrefix(Component.text(preset.label().toUpperCase(), preset.color(), TextDecoration.BOLD)
                .append(Component.text(" ", preset.color())));
        }
        return team;
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

    private record ColoredTeamPreset(NamedTextColor color, String label) {
    }
}
