package com.donutsforlife11.donutgame.api.team;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameTeam {
    private final Team bukkitTeam;
    private final Set<UUID> members = new LinkedHashSet<>();

    private final TeamManager teamManager;

    private NamedTextColor color = NamedTextColor.WHITE;
    private boolean teamGlow = true;
    private Component prefix = Component.empty();
    private Component suffix = Component.empty();
    private boolean friendlyFire = false;
    private boolean seeFriendlyInvisibles = true;
    private Team.OptionStatus nametagVisibility = Team.OptionStatus.ALWAYS;
    private Team.OptionStatus collisionRule = Team.OptionStatus.FOR_OWN_TEAM;

    public GameTeam(TeamManager teamManager, Scoreboard scoreboard) {
        this.teamManager = teamManager;
        this.bukkitTeam = scoreboard.registerNewTeam("team_" + System.identityHashCode(this));
        applyProperties();
    }

    public GameTeam addPlayer(GamePlayer player) {
        members.add(player.uuid());
        if (player.player() != null) {
            bukkitTeam.addEntity(player.player());
        }
        return this;
    }

    public GameTeam removePlayer(GamePlayer player) {
        members.remove(player.uuid());
        if (player.player() != null) {
            bukkitTeam.removeEntity(player.player());
        }
        return this;
    }

    public void remove() {
        clearMembers();
        bukkitTeam.unregister();
        teamManager.getTeamsModifiable().remove(this);
    }

    public Collection<GamePlayer> getMembers() {
        Set<GamePlayer> players = new LinkedHashSet<>();
        for (UUID uuid : members) {
            GamePlayer player = teamManager.playerManager().getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return Collections.unmodifiableSet(players);
    }

    void applyProperties() {
        bukkitTeam.color(color);
        bukkitTeam.prefix(prefix);
        bukkitTeam.suffix(suffix);
        bukkitTeam.setAllowFriendlyFire(friendlyFire);
        bukkitTeam.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        bukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, nametagVisibility);
        bukkitTeam.setOption(Team.Option.COLLISION_RULE, collisionRule);
    }

    public GameTeam setColor(NamedTextColor color) {
        this.color = color;
        applyProperties();
        return this;
    }

    public GameTeam setTeamGlow(boolean teamGlow) {
        this.teamGlow = teamGlow;
        applyProperties();
        return this;
    }

    public GameTeam setPrefix(Component prefix) {
        this.prefix = prefix;
        applyProperties();
        return this;
    }

    public GameTeam setSuffix(Component suffix) {
        this.suffix = suffix;
        applyProperties();
        return this;
    }

    public GameTeam setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
        applyProperties();
        return this;
    }

    public GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) {
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        applyProperties();
        return this;
    }

    public GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) {
        this.nametagVisibility = nametagVisibility;
        applyProperties();
        return this;
    }

    public GameTeam setCollisionRule(Team.OptionStatus collisionRule) {
        this.collisionRule = collisionRule;
        applyProperties();
        return this;
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean teamGlow() {
        return teamGlow;
    }

    public Component prefix() {
        return prefix;
    }

    public Component suffix() {
        return suffix;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public boolean seeFriendlyInvisibles() {
        return seeFriendlyInvisibles;
    }

    public Team.OptionStatus nametagVisibility() {
        return nametagVisibility;
    }

    public Team.OptionStatus collisionRule() {
        return collisionRule;
    }

    GameTeam clearMembers() {
        for (GamePlayer player : Set.copyOf(getMembers())) {
            removePlayer(player);
        }
        return this;
    }
}
