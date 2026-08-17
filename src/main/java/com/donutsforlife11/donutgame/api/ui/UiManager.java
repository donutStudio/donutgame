package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
    private static final Map<EquipmentSlot, ItemStack> HIDDEN_EQUIPMENT = Map.of(
        EquipmentSlot.HAND, ItemStack.of(Material.AIR),
        EquipmentSlot.OFF_HAND, ItemStack.of(Material.AIR),
        EquipmentSlot.HEAD, ItemStack.of(Material.AIR),
        EquipmentSlot.CHEST, ItemStack.of(Material.AIR),
        EquipmentSlot.LEGS, ItemStack.of(Material.AIR),
        EquipmentSlot.FEET, ItemStack.of(Material.AIR)
    );

    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();
    private final Set<GameGlow> glows = ConcurrentHashMap.newKeySet();
    private final GameModule module;
    private final GlowService glowService;
    private final TitlePacketTracker titleTracker;
    private boolean playerStateRefreshScheduled;
    private boolean playerStateTrackingResetQueued;

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
            player.sendTitlePart(TitlePart.TITLE, title);
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
            player.sendTitlePart(TitlePart.SUBTITLE, subtitle);
        }
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
        for (Player player : resolvePlayers(players)) {
            playSound(player, sound, category, location, volume, pitch, minVolume);
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

    public GameGlow newGlow(GameEntity entity, Collection<GamePlayer> viewers, NamedTextColor color) {
        if (entity == null || entity.bukkitEntity() == null) {
            return new GameGlow(() -> {});
        }
        Collection<Player> resolved = resolvePlayers(viewers);
        glowService.glowEntity(entity.bukkitEntity(), resolved, color);
        GameGlow glow = new GameGlow(() -> glowService.clearEntityGlow(entity.bukkitEntity(), resolved));
        glows.add(glow);
        return glow;
    }

    public GameGlow newGlow(GameEntity entity, GamePlayer viewer, NamedTextColor color) {
        return newGlow(entity, Set.of(viewer), color);
    }

    public GameGlow newGlow(GameLocation location, org.bukkit.block.data.BlockData blockData, Collection<GamePlayer> viewers, NamedTextColor color) {
        GameGlow glow = glowService.glowBlock(module.world(), location, blockData, resolvePlayers(viewers), color);
        glows.add(glow);
        return glow;
    }

    public GameGlow newGlow(GameLocation location, org.bukkit.block.data.BlockData blockData, GamePlayer viewer, NamedTextColor color) {
        return newGlow(location, blockData, Set.of(viewer), color);
    }

    public void refreshTeamGlows() {
        refreshPlayerState();
    }

    public void refreshPlayerState() {
        schedulePlayerStateRefresh(false);
    }

    public void refreshPlayerStateAfterTrackingReset() {
        schedulePlayerStateRefresh(true);
    }

    private void schedulePlayerStateRefresh(boolean trackingReset) {
        if (!module.plugin().isEnabled() || module.isTransitioning()) return;
        playerStateTrackingResetQueued = playerStateTrackingResetQueued || trackingReset;
        if (playerStateRefreshScheduled) return;
        playerStateRefreshScheduled = true;
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (module.isTransitioning()) {
                playerStateTrackingResetQueued = false;
                playerStateRefreshScheduled = false;
                return;
            }
            boolean forceTrackingReset = playerStateTrackingResetQueued;
            playerStateTrackingResetQueued = false;
            playerStateRefreshScheduled = false;
            refreshPlayerStateNow(forceTrackingReset);
        });
    }

    private void refreshPlayerStateNow(boolean forceTrackingReset) {
        if (forceTrackingReset) glowService.clearAppliedCache();
        List<GamePlayer> players = new ArrayList<>(module.playerManager().getPlayers());
        for (GamePlayer viewer : players) {
            Player viewerPlayer = viewer.player();
            if (viewerPlayer == null || !viewerPlayer.isOnline()) continue;
            for (GamePlayer subject : players) {
                if (viewer.uuid().equals(subject.uuid())) continue;
                Player subjectPlayer = subject.player();
                if (subjectPlayer == null || !subjectPlayer.isOnline() || !viewerPlayer.getWorld().equals(subjectPlayer.getWorld())) continue;
                if (subject.isSpectator()) {
                    glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                    viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                    viewerPlayer.showEntity(module.plugin(), subjectPlayer);
                    viewerPlayer.sendEquipmentChange(subjectPlayer, HIDDEN_EQUIPMENT);
                    continue;
                }
                viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                viewerPlayer.showEntity(module.plugin(), subjectPlayer);
                GameTeam viewerTeam = viewer.team();
                GameTeam subjectTeam = subject.team();
                if (viewerTeam != null && viewerTeam == subjectTeam && shouldGlowTeammate(subjectTeam)) glowService.glowEntityUsingTeamColor(subjectPlayer, Set.of(viewerPlayer));
                else glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
            }
        }
    }

    public void clear() {
        playerStateTrackingResetQueued = false;
        playerStateRefreshScheduled = false;
        for (GameSidebar sidebar : Set.copyOf(sidebars)) {
            sidebar.remove();
            sidebars.remove(sidebar);
        }
        for (GameGlow glow : Set.copyOf(glows)) {
            glow.clear();
            glows.remove(glow);
        }
        glowService.clearAppliedCache();
    }

    public void resetPlayerStateForShutdown() {
        playerStateTrackingResetQueued = false;
        playerStateRefreshScheduled = false;
        List<GamePlayer> players = new ArrayList<>(module.playerManager().getPlayers());
        for (GamePlayer viewer : players) {
            Player viewerPlayer = viewer.player();
            if (viewerPlayer == null || !viewerPlayer.isOnline()) continue;
            for (GamePlayer subject : players) {
                if (viewer.uuid().equals(subject.uuid())) continue;
                Player subjectPlayer = subject.player();
                if (subjectPlayer == null || !subjectPlayer.isOnline()) continue;
                glowService.clearEntityGlow(subjectPlayer, Set.of(viewerPlayer));
                viewerPlayer.showPlayer(module.plugin(), subjectPlayer);
                viewerPlayer.showEntity(module.plugin(), subjectPlayer);
            }
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

    private void playSound(Player player, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) {
        Location source = resolveSoundLocation(player, location);
        if (source == null) return;
        if (!canHearSoundNormally(player, source, volume)) {
            if (minVolume <= 0f) return;
            player.playSound(player.getLocation(), sound, category, minVolume, pitch);
            return;
        }
        player.playSound(source, sound, category, volume, pitch);
    }

    private Location resolveSoundLocation(Player player, GameLocation location) {
        if (location == null) return player.getLocation();
        if (module.world() == null) return null;
        return location.toBukkit(module.world().bukkitWorld());
    }

    private boolean canHearSoundNormally(Player player, Location source, float volume) {
        if (!player.getWorld().equals(source.getWorld())) return false;
        float audibleRadius = volume > 1f ? 16f * volume : 16f;
        return player.getLocation().distanceSquared(source) <= audibleRadius * audibleRadius;
    }

    private boolean shouldGlowTeammate(GameTeam team) {
        return team.teamGlow();
    }
}
