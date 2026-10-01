package com.donutsforlife11.donutgame.api.team;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.ItemSpec;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameTeam {
    private final TeamManager manager;
    private final Team team;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final int index;
    private NamedTextColor color = NamedTextColor.WHITE;
    private Component displayName;
    private boolean defaultDisplayName = true;
    private ItemStack item;
    private boolean teamGlow = true;
    private boolean friendlyFire;
    private boolean friendlyFireKillCredit;
    private Set<DamageType> friendlyFireDamageTypes = new LinkedHashSet<>(Set.of(DamageType.EXPLOSION, DamageType.PLAYER_EXPLOSION));
    private boolean seeFriendlyInvisibles = true;
    private Team.OptionStatus nametagVisibility = Team.OptionStatus.ALWAYS;
    private Team.OptionStatus collisionRule = Team.OptionStatus.FOR_OWN_TEAM;
    private boolean removed;

    GameTeam(TeamManager manager, Scoreboard scoreboard, int index) {
        this.manager = manager;
        this.index = index;
        this.team = scoreboard.registerNewTeam("team_" + Integer.toUnsignedString(System.identityHashCode(this), 36));
        this.displayName = defaultDisplayName();
        applyProperties();
    }

    public GameTeam addPlayer(GamePlayer player) {
        if (removed || player == null || !players.add(player.uuid())) {
            return this;
        }
        manager.assign(this, player);
        syncPlayer(player);
        refreshTeamGlow();
        return this;
    }

    public GameTeam removePlayer(GamePlayer player) {
        if (player == null || !players.remove(player.uuid())) {
            return this;
        }
        manager.unassign(this, player);
        clearGlow(player);
        Player bukkitPlayer = player.bukkitPlayer();
        if (!removed && bukkitPlayer != null) {
            team.removeEntry(bukkitPlayer.getName());
            player.restoreExternalScoreboard(bukkitPlayer);
        }
        refreshTeamGlow();
        return this;
    }

    public GameTeam clearPlayers() {
        for (GamePlayer player : Set.copyOf(getPlayers())) {
            removePlayer(player);
        }
        return this;
    }

    public void remove() {
        if (removed) {
            return;
        }
        clearPlayers();
        removed = true;
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
        }
        manager.teamsModifiable().remove(this);
    }

    public Collection<GamePlayer> getPlayers() {
        return filteredPlayers(player -> true);
    }

    public Collection<GamePlayer> getSpectators() {
        return filteredPlayers(GamePlayer::isSpectator);
    }

    public Collection<GamePlayer> getNonSpectators() {
        return filteredPlayers(player -> !player.isSpectator());
    }

    public boolean allSpectators() {
        for (GamePlayer player : getPlayers()) {
            if (!player.isSpectator()) {
                return false;
            }
        }
        return true;
    }

    public GameTeam setColor(NamedTextColor color) {
        this.color = color == null ? NamedTextColor.WHITE : color;
        if (defaultDisplayName) {
            displayName = defaultDisplayName();
        }
        applyProperties();
        return this;
    }

    public NamedTextColor color() {
        return team.color() instanceof NamedTextColor named ? named : color;
    }

    public GameTeam setDisplayName(Component displayName) {
        this.displayName = displayName == null ? defaultDisplayName() : displayName;
        this.defaultDisplayName = displayName == null;
        team.displayName(this.displayName);
        return this;
    }

    public Component displayName() {
        return displayName;
    }

    public GameTeam setItem(ItemSpec item) {
        return setItem(item == null ? null : item.createItem());
    }

    public GameTeam setItem(GameItem item) {
        this.item = item == null ? null : item.copyBukkitItem();
        return this;
    }

    public GameItem item() {
        return GameItem.from(item);
    }

    public GameTeam setTeamGlow(boolean glow) {
        teamGlow = glow;
        refreshTeamGlow();
        return this;
    }

    public boolean teamGlow() {
        return teamGlow && teamGlowAvailable();
    }

    public GameTeam setPrefix(Component prefix) {
        team.prefix(prefix == null ? Component.empty() : prefix);
        return this;
    }

    public Component prefix() {
        return team.prefix();
    }

    public GameTeam setSuffix(Component suffix) {
        team.suffix(suffix == null ? Component.empty() : suffix);
        return this;
    }

    public Component suffix() {
        return team.suffix();
    }

    public GameTeam setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
        team.setAllowFriendlyFire(friendlyFire);
        return this;
    }

    public GameTeam setFriendlyFireDamageTypes(Collection<DamageType> damageTypes) {
        friendlyFireDamageTypes = new LinkedHashSet<>();
        if (damageTypes != null) {
            for (DamageType damageType : damageTypes) {
                if (damageType != null) {
                    friendlyFireDamageTypes.add(damageType);
                }
            }
        }
        return this;
    }

    public GameTeam setFriendlyFireKillCredit(boolean friendlyFireKillCredit) {
        this.friendlyFireKillCredit = friendlyFireKillCredit;
        return this;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public Collection<DamageType> friendlyFireDamageTypes() {
        return Collections.unmodifiableSet(friendlyFireDamageTypes);
    }

    public boolean friendlyFireKillCredit() {
        return friendlyFireKillCredit;
    }

    public boolean allowsFriendlyFireDamage(DamageType damageType) {
        return friendlyFire || friendlyFireDamageTypes.contains(damageType);
    }

    public GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) {
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        team.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        return this;
    }

    public boolean seeFriendlyInvisibles() {
        return team.canSeeFriendlyInvisibles();
    }

    public GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) {
        this.nametagVisibility = nametagVisibility == null ? Team.OptionStatus.ALWAYS : nametagVisibility;
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, this.nametagVisibility);
        return this;
    }

    public Team.OptionStatus nametagVisibility() {
        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public GameTeam setCollisionRule(Team.OptionStatus collisionRule) {
        this.collisionRule = collisionRule == null ? Team.OptionStatus.FOR_OWN_TEAM : collisionRule;
        team.setOption(Team.Option.COLLISION_RULE, this.collisionRule);
        return this;
    }

    public Team.OptionStatus collisionRule() {
        return team.getOption(Team.Option.COLLISION_RULE);
    }

    public Team bukkitTeam() {
        return team;
    }

    void syncPlayer(GamePlayer player) {
        Player bukkitPlayer = player == null ? null : player.bukkitPlayer();
        if (removed || bukkitPlayer == null) {
            return;
        }
        player.captureExternalScoreboard(bukkitPlayer);
        bukkitPlayer.setScoreboard(team.getScoreboard());
        team.addEntry(bukkitPlayer.getName());
        syncGlow(player);
    }

    private void applyProperties() {
        team.color(color);
        team.displayName(displayName);
        team.setAllowFriendlyFire(friendlyFire);
        team.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, nametagVisibility);
        team.setOption(Team.Option.COLLISION_RULE, collisionRule);
        for (GamePlayer player : getPlayers()) {
            syncPlayer(player);
        }
    }

    private void refreshTeamGlow() {
        for (GamePlayer player : getPlayers()) {
            syncGlow(player);
        }
    }

    private void syncGlow(GamePlayer player) {
        if (player == null) {
            return;
        }
        if (!teamGlow()) {
            clearGlow(player);
            return;
        }
        GlowService glowService = manager.playerManager().module().plugin().glowService();
        if (glowService == null || !glowService.setPacketGlowing(player.bukkitEntity(), true, () -> glowViewers(player), color())) {
            clearGlow(player);
        }
    }

    private void clearGlow(GamePlayer player) {
        GlowService glowService = manager.playerManager().module().plugin().glowService();
        if (glowService != null && player != null) {
            glowService.setPacketGlowing(player.bukkitEntity(), false, Set::of, color());
        }
    }

    private Collection<GamePlayer> glowViewers(GamePlayer subject) {
        Set<GamePlayer> viewers = new LinkedHashSet<>();
        for (GamePlayer player : getPlayers()) {
            if (player != subject) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private boolean teamGlowAvailable() {
        GlowService glowService = manager.playerManager().module().plugin().glowService();
        return glowService != null && glowService.packetGlowAvailable();
    }

    private Collection<GamePlayer> filteredPlayers(Predicate<GamePlayer> filter) {
        Set<GamePlayer> matches = new LinkedHashSet<>();
        for (UUID uuid : players) {
            GamePlayer player = manager.playerManager().getPlayer(uuid);
            if (player != null && filter.test(player)) {
                matches.add(player);
            }
        }
        return Collections.unmodifiableSet(matches);
    }

    private Component defaultDisplayName() {
        return Component.text("Team " + (index + 1), color);
    }
}
