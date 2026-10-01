package com.donutsforlife11.donutgame.internal.game;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

public class SpectatorService {
    private final Donutgame plugin;

    public SpectatorService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public void enable() {
    }

    public void disable() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player hidden : Bukkit.getOnlinePlayers()) {
                viewer.showEntity(plugin, hidden);
                viewer.showPlayer(plugin, hidden);
            }
        }
    }

    public void showSpectator(GamePlayer spectator) {
        Player player = spectator == null ? null : spectator.bukkitPlayer();
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.hidePlayer(plugin, player);
            }
            listSpectator(viewer, player);
        }
    }

    public void hideSpectator(GamePlayer spectator) {
        Player player = spectator == null ? null : spectator.bukkitPlayer();
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
            listPlayer(viewer, player, false);
        }
    }

    public void refreshForViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        for (GameModule game : plugin.moduleService().activeGames().values()) {
            for (GamePlayer spectator : game.playerManager().getSpectators()) {
                Player player = spectator.bukkitPlayer();
                if (player == null) {
                    continue;
                }
                if (!viewer.equals(player)) {
                    viewer.hidePlayer(plugin, player);
                }
                listSpectator(viewer, player);
            }
        }
    }

    public boolean isSpectator(Player player) {
        GameModule game = plugin.moduleService().getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        return gamePlayer != null && gamePlayer.isSpectator();
    }

    private void listSpectator(Player viewer, Player player) {
        listPlayer(viewer, player, true);
    }

    private void listPlayer(Player viewer, Player player, boolean spectator) {
        if (viewer == null || player == null) {
            return;
        }
        if (sendPlayerInfo(viewer, player, spectator)) {
            return;
        }
        try {
            viewer.listPlayer(player);
        } catch (IllegalArgumentException ignored) {
            // Paper requires normal visibility here; PacketEvents handles hidden spectators when present.
        }
    }

    private boolean sendPlayerInfo(Player viewer, Player player, boolean spectator) {
        try {
            var api = PacketEvents.getAPI();
            if (api == null) {
                return false;
            }
            var protocolManager = api.getProtocolManager();
            Object channel = protocolManager.getChannel(viewer.getUniqueId());
            if (channel == null) {
                return false;
            }
            PlayerInfo info = new PlayerInfo(
                userProfile(player),
                true,
                player.getPing(),
                spectator ? GameMode.SPECTATOR : packetGameMode(player),
                null,
                null
            );
            EnumSet<Action> actions = EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE, Action.UPDATE_LATENCY);
            if (!viewer.equals(player)) {
                actions.add(Action.ADD_PLAYER);
            }
            protocolManager.sendPacket(channel, new WrapperPlayServerPlayerInfoUpdate(
                actions,
                List.of(info)
            ));
            return true;
        } catch (NoClassDefFoundError | RuntimeException exception) {
            return false;
        }
    }

    private GameMode packetGameMode(Player player) {
        return switch (player.getGameMode()) {
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
            case SURVIVAL -> GameMode.SURVIVAL;
        };
    }

    private UserProfile userProfile(Player player) {
        List<TextureProperty> properties = new ArrayList<>();
        for (var property : player.getPlayerProfile().getProperties()) {
            properties.add(new TextureProperty(property.getName(), property.getValue(), property.getSignature()));
        }
        return new UserProfile(player.getUniqueId(), player.getName(), properties);
    }
}
