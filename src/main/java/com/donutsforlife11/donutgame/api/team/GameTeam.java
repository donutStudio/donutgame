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
import com.donutsforlife11.donutgame.api.item.GameItemType;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

import com.donutsforlife11.donutgame.internal.game.GameScope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GameTeam {
    private final TeamManager manager;
    private final Team team;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final int index;
    private boolean defaultDisplayName = true;
    private ItemStack item;
    private boolean teamGlow = true;
    private boolean friendlyFireKillCredit;
    private Set<DamageType> friendlyFireDamageTypes = new LinkedHashSet<>(Set.of(DamageType.EXPLOSION, DamageType.PLAYER_EXPLOSION));
    private Team.OptionStatus collisionRule = Team.OptionStatus.FOR_OWN_TEAM;
    private boolean removed;

    GameTeam(TeamManager manager, Scoreboard scoreboard, int index) {
        this.manager = manager;
        this.index = index;
        this.team = scoreboard.registerNewTeam("team_" + Integer.toUnsignedString(System.identityHashCode(this), 36));
        team.color(NamedTextColor.WHITE);
        team.displayName(defaultDisplayName());
        team.setAllowFriendlyFire(false);
        team.setCanSeeFriendlyInvisibles(true);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, effectiveCollisionRule());
    }

    public GameTeam addPlayer(GamePlayer player) {
        requireActive();
        if (player != null) {
            GameScope.requirePlayer(manager.playerManager().module(), player);
        }
        if (player == null || !players.add(player.uuid())) {
            return this;
        }
        manager.assign(this, player);
        syncPlayer(player);
        refreshTeamGlow();
        return this;
    }

    public GameTeam removePlayer(GamePlayer player) {
        if (player != null) {
            GameScope.requirePlayer(manager.playerManager().module(), player);
        }
        if (player == null || !players.remove(player.uuid())) {
            return this;
        }
        manager.unassign(this, player);
        clearGlow(player);
        Player bukkitPlayer = GamePlayerHandle.bukkitPlayer(player);
        if (!removed) {
            team.removeEntry(player.name());
        }
        if (!removed && bukkitPlayer != null) {
            GamePlayerHandle.restoreExternalScoreboard(player, bukkitPlayer);
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
        return filteredPlayers(player -> player.isSpectator() || !player.isOnline());
    }

    public Collection<GamePlayer> getNonSpectators() {
        return filteredPlayers(player -> !player.isSpectator() && player.isOnline());
    }

    public boolean allSpectators() {
        for (GamePlayer player : getPlayers()) {
            if (!player.isSpectator() && player.isOnline()) {
                return false;
            }
        }
        return true;
    }

    public GameTeam setColor(NamedTextColor color) {
        requireActive();
        team.color(color == null ? NamedTextColor.WHITE : color);
        if (defaultDisplayName) {
            team.displayName(defaultDisplayName());
        }
        return this;
    }

    public NamedTextColor color() {
        return team.color() instanceof NamedTextColor named ? named : NamedTextColor.WHITE;
    }

    public GameTeam setDisplayName(Component displayName) {
        requireActive();
        this.defaultDisplayName = displayName == null;
        team.displayName(defaultDisplayName ? defaultDisplayName() : displayName);
        return this;
    }

    public Component displayName() {
        return team.displayName();
    }

    public GameTeam setItem(GameItemType item) {
        return setItem(item == null ? null : item.create());
    }

    public GameTeam setItem(GameItem item) {
        requireActive();
        this.item = item == null ? null : item.copyBukkitItem();
        return this;
    }

    public GameItem item() {
        return GameItem.from(item);
    }

    public GameTeam setTeamGlow(boolean glow) {
        requireActive();
        teamGlow = glow;
        refreshTeamGlow();
        return this;
    }

    public boolean teamGlow() {
        return teamGlow && teamGlowAvailable();
    }

    public GameTeam setPrefix(Component prefix) {
        requireActive();
        team.prefix(prefix == null ? Component.empty() : prefix);
        return this;
    }

    public Component prefix() {
        return team.prefix();
    }

    public GameTeam setSuffix(Component suffix) {
        requireActive();
        team.suffix(suffix == null ? Component.empty() : suffix);
        return this;
    }

    public Component suffix() {
        return team.suffix();
    }

    public GameTeam setFriendlyFire(boolean friendlyFire) {
        requireActive();
        team.setAllowFriendlyFire(friendlyFire);
        return this;
    }

    public GameTeam setFriendlyFireDamageTypes(Collection<DamageType> damageTypes) {
        requireActive();
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
        requireActive();
        this.friendlyFireKillCredit = friendlyFireKillCredit;
        return this;
    }

    public boolean friendlyFire() {
        return team.allowFriendlyFire();
    }

    public Collection<DamageType> friendlyFireDamageTypes() {
        return Collections.unmodifiableSet(friendlyFireDamageTypes);
    }

    public boolean friendlyFireKillCredit() {
        return friendlyFireKillCredit;
    }

    public boolean allowsFriendlyFireDamage(DamageType damageType) {
        return friendlyFire() || friendlyFireDamageTypes.contains(damageType);
    }

    public GameTeam setSeeFriendlyInvisibles(boolean seeFriendlyInvisibles) {
        requireActive();
        team.setCanSeeFriendlyInvisibles(seeFriendlyInvisibles);
        return this;
    }

    public boolean seeFriendlyInvisibles() {
        return team.canSeeFriendlyInvisibles();
    }

    public GameTeam setNametagVisibility(Team.OptionStatus nametagVisibility) {
        requireActive();
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, nametagVisibility == null ? Team.OptionStatus.ALWAYS : nametagVisibility);
        return this;
    }

    public Team.OptionStatus nametagVisibility() {
        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public GameTeam setCollisionRule(Team.OptionStatus collisionRule) {
        requireActive();
        this.collisionRule = collisionRule == null ? Team.OptionStatus.FOR_OWN_TEAM : collisionRule;
        team.setOption(Team.Option.COLLISION_RULE, effectiveCollisionRule());
        return this;
    }

    public Team.OptionStatus collisionRule() {
        return collisionRule;
    }

    public Team.OptionStatus effectiveCollisionRuleSetting() {
        return team.getOption(Team.Option.COLLISION_RULE);
    }

    private void requireActive() {
        GameScope.requireActive(manager.playerManager().module());
        if (removed) {
            throw new IllegalStateException("Team has been removed.");
        }
    }

    void syncPlayer(GamePlayer player) {
        Player bukkitPlayer = GamePlayerHandle.bukkitPlayer(player);
        if (removed || bukkitPlayer == null) {
            return;
        }
        GamePlayerHandle.captureExternalScoreboard(player, bukkitPlayer);
        bukkitPlayer.setScoreboard(team.getScoreboard());
        team.addEntry(bukkitPlayer.getName());
        syncGlow(player);
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
        GlowService glowService = manager.implementationPlugin().glowService();
        if (glowService == null || !glowService.setPacketGlowing(GameEntityHandle.bukkitEntity(player), true, () -> glowViewers(player), null)) {
            clearGlow(player);
        }
    }

    private void clearGlow(GamePlayer player) {
        GlowService glowService = manager.implementationPlugin().glowService();
        if (glowService != null && player != null) {
            glowService.setPacketGlowing(GameEntityHandle.bukkitEntity(player), false, Set::of, null);
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
        GlowService glowService = manager.implementationPlugin().glowService();
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
        return Component.text("Team " + (index + 1), color());
    }

    private Team.OptionStatus effectiveCollisionRule() {
        return manager.playerManager().module().world().playerCollisionsEnabled()
            ? collisionRule
            : Team.OptionStatus.NEVER;
    }
}
