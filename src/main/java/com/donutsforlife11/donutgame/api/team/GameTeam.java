package com.donutsforlife11.donutgame.api.team;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameTeam {
    private final TeamManager teamManager;
    private final Team bukkitTeam;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final int defaultIndex;

    private NamedTextColor color = NamedTextColor.WHITE;
    private boolean teamGlow = true;
    private Component prefix = Component.empty();
    private Component suffix = Component.empty();
    private Component displayName;
    private boolean defaultDisplayName = true;
    private boolean friendlyFire;
    private boolean seeFriendlyInvisibles = true;
    private Team.OptionStatus nametagVisibility = Team.OptionStatus.ALWAYS;
    private Team.OptionStatus collisionRule = Team.OptionStatus.FOR_OWN_TEAM;
    private boolean removed;

    public GameTeam(TeamManager teamManager, Scoreboard scoreboard, int defaultIndex) {
        this.teamManager = teamManager;
        this.defaultIndex = defaultIndex;
        bukkitTeam = scoreboard.registerNewTeam("team_" + Integer.toUnsignedString(System.identityHashCode(this), 36));
        displayName = defaultDisplayName();
        applyProperties();
    }

    public GameTeam addPlayer(GamePlayer player) {
        if (removed) {
            return this;
        }
        if (player == null || !members.add(player.uuid())) {
            return this;
        }
        teamManager.assign(this, player);
        if (player.player() != null) {
            player.player().setScoreboard(bukkitTeam.getScoreboard());
            bukkitTeam.addEntry(player.player().getName());
        }
        teamManager.playerManager().module().uiManager().refreshPlayerState();
        return this;
    }

    public GameTeam removePlayer(GamePlayer player) {
        if (player == null || !members.remove(player.uuid())) {
            return this;
        }
        teamManager.unassign(this, player);
        if (!removed && player.player() != null) {
            bukkitTeam.removeEntry(player.player().getName());
            player.player().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        teamManager.playerManager().module().uiManager().refreshPlayerState();
        return this;
    }

    public void remove() {
        if (removed) {
            return;
        }
        teamManager.playerManager().setTeamSpectatable(this, false);
        teamManager.playerManager().removeSpectatableTeam(this);
        clearMembers();
        removed = true;
        try {
            bukkitTeam.unregister();
        } catch (IllegalStateException ignored) {
        }
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
        if (removed) {
            return;
        }
        bukkitTeam.color(color);
        bukkitTeam.prefix(prefix);
        bukkitTeam.suffix(suffix);
        bukkitTeam.setAllowFriendlyFire(friendlyFire);
        bukkitTeam.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        bukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, nametagVisibility);
        bukkitTeam.setOption(Team.Option.COLLISION_RULE, collisionRule);
        syncOnlineMembers();
        teamManager.playerManager().module().uiManager().refreshPlayerState();
    }

    GameTeam clearMembers() {
        for (GamePlayer player : Set.copyOf(getMembers())) {
            removePlayer(player);
        }
        return this;
    }

    public GameTeam setColor(NamedTextColor color) {
        this.color = color;
        if (defaultDisplayName) displayName = defaultDisplayName();
        applyProperties();
        return this;
    }

    public GameTeam setDisplayName(Component displayName) {
        this.displayName = displayName == null ? defaultDisplayName() : displayName;
        defaultDisplayName = displayName == null;
        return this;
    }

    public Component displayName() {
        return displayName;
    }

    public GameTeam setSpectatable(boolean spectatable) {
        teamManager.playerManager().setTeamSpectatable(this, spectatable);
        return this;
    }

    public boolean isSpectatable() {
        return teamManager.playerManager().spectatableTeams().contains(this);
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
        return bukkitTeam.color() instanceof NamedTextColor named ? named : color;
    }

    public boolean teamGlow() {
        return teamGlow;
    }

    public Component prefix() {
        return bukkitTeam.prefix();
    }

    public Component suffix() {
        return bukkitTeam.suffix();
    }

    public boolean friendlyFire() {
        return bukkitTeam.allowFriendlyFire();
    }

    public boolean seeFriendlyInvisibles() {
        return bukkitTeam.canSeeFriendlyInvisibles();
    }

    public Team.OptionStatus nametagVisibility() {
        return bukkitTeam.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public Team.OptionStatus collisionRule() {
        return bukkitTeam.getOption(Team.Option.COLLISION_RULE);
    }

    private void syncOnlineMembers() {
        for (GamePlayer player : getMembers()) {
            if (player.player() == null) continue;
            player.player().setScoreboard(bukkitTeam.getScoreboard());
            bukkitTeam.addEntry(player.player().getName());
        }
    }

    private Component defaultDisplayName() {
        return Component.text("Team " + (defaultIndex + 1), color);
    }
}
