package com.donutsforlife11.donutgame.api.teams;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;

public class GameTeam {
    private final String id;
    private final Team bukkitTeam;

    private TeamProperties properties;

    public GameTeam(String id, Scoreboard scoreboard, TeamProperties properties) {
        this.id = id;
        this.properties = properties;
        properties.displayName = Component.text(id);

        this.bukkitTeam = scoreboard.registerNewTeam(id);
        applyProperties();
    }

    public String getId() {
        return id;
    }

    public TeamProperties getProperties() {
        return properties;
    }

    public void setProperties(TeamProperties properties) {
        this.properties = properties;
        applyProperties();
    }

    public boolean hasPlayer(Player player) {
        return bukkitTeam.hasEntity(player);
    }

    public int getSize() {
        return bukkitTeam.getEntries().size();
    }

    public void addPlayer(Player player) {
        bukkitTeam.addEntity(player);
    }

    public void removePlayer(Player player) {
        bukkitTeam.removeEntity(player);
    }

    public Team getBukkitTeam() {
        return bukkitTeam;
    }

    public void unregister() {
        bukkitTeam.unregister();
    }

    private void applyProperties() {
        bukkitTeam.displayName(properties.displayName);
        bukkitTeam.prefix(properties.prefix);
        bukkitTeam.suffix(properties.suffix);

        bukkitTeam.color(properties.vanillaColor);

        bukkitTeam.setAllowFriendlyFire(properties.friendlyFire);
        bukkitTeam.setCanSeeFriendlyInvisibles(properties.seeFriendlyInvisibles);

        bukkitTeam.setOption(
                Team.Option.NAME_TAG_VISIBILITY,
                properties.nameTagVisibility
        );

        bukkitTeam.setOption(
                Team.Option.COLLISION_RULE,
                properties.collisionRule
        );
    }
}