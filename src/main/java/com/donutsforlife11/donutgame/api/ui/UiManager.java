package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;
import com.donutsforlife11.donutgame.api.ui.title.TitlePacketTracker;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.ui.GlowService;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;

public class UiManager {
    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();
    private final Set<GameGlow> glows = ConcurrentHashMap.newKeySet();
    private final GameModule module;
    private final GlowService glowService;
    private final TitlePacketTracker titleTracker;

    public UiManager(GameModule module, Donutgame plugin) {
        this.module = module;
        this.glowService = plugin.glowService();
        this.titleTracker = new TitlePacketTracker(plugin);
    }

    public void title(GamePlayer player, Component title) {
        title(Set.of(player), title);
    }

    public void title(Collection<GamePlayer> players, Component title) {
        for (Player player : resolvePlayers(players)) {
            player.sendTitlePart(TitlePart.TITLE, title == null ? Component.empty() : title);
            titleTracker.markTitle(player);
        }
    }

    public void subtitle(GamePlayer player, Component subtitle) {
        subtitle(Set.of(player), subtitle);
    }

    public void subtitle(Collection<GamePlayer> players, Component subtitle) {
        for (Player player : resolvePlayers(players)) {
            if (!titleTracker.hasActiveTitle(player)) {
                player.sendTitlePart(TitlePart.TITLE, Component.empty());
                titleTracker.markTitle(player);
            }
            player.sendTitlePart(TitlePart.SUBTITLE, subtitle == null ? Component.empty() : subtitle);
        }
    }

    public void actionbar(GamePlayer player, Component actionbar) {
        actionbar(Set.of(player), actionbar);
    }

    public void actionbar(Collection<GamePlayer> players, Component actionbar) {
        Audience.audience(resolvePlayers(players)).sendActionBar(actionbar == null ? Component.empty() : actionbar);
    }

    public void chat(GamePlayer player, Component message) {
        chat(Set.of(player), message);
    }

    public void chat(Collection<GamePlayer> players, Component message) {
        Audience.audience(resolvePlayers(players)).sendMessage(message == null ? Component.empty() : message);
    }

    public void gameMessage(GamePlayer player, Component message) {
        gameMessage(Set.of(player), message);
    }

    public void gameMessage(Collection<GamePlayer> players, Component message) {
        Component body = message == null ? Component.empty() : message.decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.FALSE);
        chat(players, Component.empty()
            .append(Component.text("Game > ", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(body));
    }

    public void playSound(GamePlayer player, Sound sound, float volume, float pitch) {
        playSound(Set.of(player), sound, volume, pitch);
    }

    public void playSound(GamePlayer player, Sound sound) {
        playSound(Set.of(player), sound);
    }

    public void playSound(Collection<GamePlayer> players, Sound sound) {
        playSound(players, sound, SoundCategory.MASTER, null, 1f, 1f, 0f);
    }

    public void playSound(Collection<GamePlayer> players, Sound sound, float volume, float pitch) {
        playSound(players, sound, SoundCategory.MASTER, null, volume, pitch, 0f);
    }

    public void playSound(GamePlayer player, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) {
        playSound(Set.of(player), sound, category, location, volume, pitch, minVolume);
    }

    public void playSound(Collection<GamePlayer> players, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) {
        if (sound == null) return;
        for (Player player : resolvePlayers(players)) {
            Location source = location == null || module.world() == null ? player.getLocation() : location.toBukkit(module.world().bukkitWorld());
            if (!player.getWorld().equals(source.getWorld())) {
                if (minVolume > 0) player.playSound(player.getLocation(), sound, category, minVolume, pitch);
                continue;
            }
            player.playSound(source, sound, category, volume, pitch);
        }
    }

    public GameSidebar newSidebar() {
        GameSidebar sidebar = new GameSidebar(module);
        sidebars.add(sidebar);
        return sidebar;
    }

    public Set<GameSidebar> sidebars() {
        return Set.copyOf(sidebars);
    }

    public GameGlow newGlow(GameEntity entity, Collection<GamePlayer> viewers, net.kyori.adventure.text.format.NamedTextColor color) {
        if (entity == null || entity.bukkitEntity() == null) return new GameGlow(() -> {});
        Collection<Player> resolved = resolvePlayers(viewers);
        glowService.glowEntity(entity.bukkitEntity(), resolved, color);
        GameGlow glow = new GameGlow(() -> glowService.clearEntityGlow(entity.bukkitEntity(), resolved));
        glows.add(glow);
        return glow;
    }

    public GameGlow newGlow(GameEntity entity, GamePlayer viewer, net.kyori.adventure.text.format.NamedTextColor color) {
        return newGlow(entity, Set.of(viewer), color);
    }

    public void refreshTeamGlows() {
        refreshPlayerState();
    }

    public void refreshPlayerState() {
        for (GamePlayer viewer : module.playerManager().getPlayers()) {
            Player viewerPlayer = viewer.player();
            if (viewerPlayer == null) continue;
            for (GamePlayer subject : module.playerManager().getPlayers()) {
                Player subjectPlayer = subject.player();
                if (subjectPlayer == null || viewer.uuid().equals(subject.uuid())) continue;
                if (subject.isSpectator()) {
                    viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                    glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                } else {
                    viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                    GameTeam viewerTeam = viewer.team();
                    GameTeam subjectTeam = subject.team();
                    if (viewerTeam != null && viewerTeam == subjectTeam && subjectTeam.teamGlow()) {
                        glowService.glowEntity(subjectPlayer, Set.of(viewerPlayer), subjectTeam.color());
                    } else {
                        glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                    }
                }
            }
        }
    }

    public void refreshPlayerStateAfterTrackingReset() {
        refreshPlayerState();
    }

    public void refreshPlayerStateAfterTrackingReset(GamePlayer player) {
        refreshPlayerState();
    }

    public void refreshPlayerPresentation(GamePlayer subject) {
        refreshPlayerState();
    }

    public void clear() {
        for (GameSidebar sidebar : Set.copyOf(sidebars)) {
            sidebar.remove();
            sidebars.remove(sidebar);
        }
        clearRoundScoped();
        glowService.clearAppliedCache();
    }

    public void clearRoundScoped() {
        for (GameGlow glow : Set.copyOf(glows)) {
            glow.clear();
            glows.remove(glow);
        }
    }

    public void resetPlayerStateForShutdown() {
        for (GamePlayer viewer : module.playerManager().getPlayers()) {
            Player viewerPlayer = viewer.player();
            if (viewerPlayer == null) continue;
            for (GamePlayer subject : module.playerManager().getPlayers()) {
                Player subjectPlayer = subject.player();
                if (subjectPlayer != null) {
                    viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                    glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                }
            }
        }
    }

    private Collection<Player> resolvePlayers(Collection<GamePlayer> players) {
        List<Player> resolved = new ArrayList<>();
        if (players == null) return resolved;
        for (GamePlayer player : players) {
            Player bukkitPlayer = player == null ? null : player.player();
            if (bukkitPlayer != null) resolved.add(bukkitPlayer);
        }
        return resolved;
    }
}
