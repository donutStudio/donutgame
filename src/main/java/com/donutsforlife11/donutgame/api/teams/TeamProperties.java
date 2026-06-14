package com.donutsforlife11.donutgame.api.teams;

import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TeamProperties {
    public Component displayName = Component.text("Team");
    public Component prefix = Component.empty();
    public Component suffix = Component.empty();

    public NamedTextColor vanillaColor = NamedTextColor.WHITE;

    public boolean friendlyFire = false;
    public boolean seeFriendlyInvisibles = true;

    public Team.OptionStatus nameTagVisibility = Team.OptionStatus.ALWAYS;
    public Team.OptionStatus collisionRule = Team.OptionStatus.ALWAYS;
}
