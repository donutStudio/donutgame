package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;
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
    private boolean playerStateRefreshScheduled;

    public UiManager(GameModule module, Donutgame plugin) {
        this.module = module;
        this.glowService = plugin.glowService();
    }

    public void title(GamePlayer player, Component title) {
        title(Set.of(player), title);
    }

    public void title(Collection<GamePlayer> players, Component title) {
        Audience.audience(resolvePlayers(players)).sendTitlePart(TitlePart.TITLE, title);
    }

    public void subtitle(GamePlayer player, Component subtitle) {
        subtitle(Set.of(player), subtitle);
    }

    public void subtitle(Collection<GamePlayer> players, Component subtitle) {
        for (Player player : resolvePlayers(players)) player.sendTitlePart(TitlePart.SUBTITLE, subtitle);
    }

    public void actionbar(GamePlayer player, Component actionbar) {
        actionbar(Set.of(player), actionbar);
    }

    public void actionbar(Collection<GamePlayer> players, Component actionbar) {
        Audience.audience(resolvePlayers(players)).sendActionBar(actionbar);
    }

    public void chat(GamePlayer player, Component message) {
        chat(Set.of(player), message);
    }

    public void chat(Collection<GamePlayer> players, Component message) {
        Audience.audience(resolvePlayers(players)).sendMessage(message);
    }

    public void gameMessage(GamePlayer player, Component message) {
        gameMessage(Set.of(player), message);
    }

    public void gameMessage(Collection<GamePlayer> players, Component message) {
        chat(players, Component.text().append(Component.text("Game > ", NamedTextColor.GREEN, TextDecoration.BOLD)).append(message).build());
    }

    public void sound(GamePlayer player, Sound sound, float volume, float pitch) {
        sound(Set.of(player), sound, volume, pitch);
    }

    public void sound(Collection<GamePlayer> players, Sound sound, float volume, float pitch) {
        for (Player player : resolvePlayers(players)) { 
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public GameSidebar newSidebar() {
        GameSidebar sidebar = new GameSidebar(module);
        sidebars.add(sidebar);
        return sidebar;
    }

    public Set<GameSidebar> sidebars() {
        return sidebars;
    }

    public GameGlow glow(GameEntity entity, Collection<GamePlayer> viewers, NamedTextColor color) {
        if (entity == null || entity.bukkitEntity() == null) {
            return new GameGlow(() -> {});
        }
        Collection<Player> resolved = resolvePlayers(viewers);
        glowService.glowEntity(entity.bukkitEntity(), resolved, color);
        GameGlow glow = new GameGlow(() -> glowService.clearEntityGlow(entity.bukkitEntity(), resolved));
        glows.add(glow);
        return glow;
    }

    public GameGlow glow(GameEntity entity, GamePlayer viewer, NamedTextColor color) {
        return glow(entity, Set.of(viewer), color);
    }

    public GameGlow glow(GameLocation location, org.bukkit.block.data.BlockData blockData, Collection<GamePlayer> viewers, NamedTextColor color) {
        GameGlow glow = glowService.glowBlock(module.world(), location, blockData, resolvePlayers(viewers), color);
        glows.add(glow);
        return glow;
    }

    public GameGlow glow(GameLocation location, org.bukkit.block.data.BlockData blockData, GamePlayer viewer, NamedTextColor color) {
        return glow(location, blockData, Set.of(viewer), color);
    }

    public void refreshTeamGlows() {
        refreshPlayerState();
    }

    public void refreshPlayerState() {
        if (playerStateRefreshScheduled) return;
        playerStateRefreshScheduled = true;
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            playerStateRefreshScheduled = false;
            refreshPlayerStateNow();
        });
    }

    private void refreshPlayerStateNow() {
        List<GamePlayer> players = new ArrayList<>(module.playerManager().getPlayers());
        for (GamePlayer viewer : players) {
            Player viewerPlayer = viewer.player();
            if (viewerPlayer == null || !viewerPlayer.isOnline()) continue;
            for (GamePlayer subject : players) {
                if (viewer.uuid().equals(subject.uuid())) continue;
                Player subjectPlayer = subject.player();
                if (subjectPlayer == null || !subjectPlayer.isOnline() || !viewerPlayer.getWorld().equals(subjectPlayer.getWorld())) continue;
                if (subject.isSpectator()) {
                    viewerPlayer.hidePlayer(module.plugin(), subjectPlayer);
                    glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                    continue;
                }
                viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                GameTeam viewerTeam = viewer.team();
                GameTeam subjectTeam = subject.team();
                if (viewerTeam != null && viewerTeam == subjectTeam && subjectTeam.teamGlow()) glowService.glowEntity(subjectPlayer, Set.of(viewerPlayer), subjectTeam.color());
                else glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
            }
        }
    }

    public void clear() {
        for (GameSidebar sidebar : Set.copyOf(sidebars)) {
            sidebar.delete();
            sidebars.remove(sidebar);
        }
        for (GameGlow glow : Set.copyOf(glows)) {
            glow.clear();
            glows.remove(glow);
        }
    }

    private Collection<Player> resolvePlayers(Collection<GamePlayer> players) {
        List<Player> resolved = new ArrayList<>(players.size());
        for (GamePlayer player : players) {
            Player bukkitPlayer = player == null ? null : player.player();
            if (bukkitPlayer != null) {
                resolved.add(bukkitPlayer);
            }
        }
        return resolved;
    }
}
