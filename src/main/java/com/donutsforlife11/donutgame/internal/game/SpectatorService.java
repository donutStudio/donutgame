package com.donutsforlife11.donutgame.internal.game;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;

public class SpectatorService {
    private static final String SELF_SPECTATOR_PROFILE_PREFIX = "donutgame:self-spectator:";

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
                removeSelfSpectatorProfile(viewer, hidden);
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

    public void refreshPlayerList(Player viewer, Player player) {
        listPlayer(viewer, player, false);
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
                !spectator || !viewer.equals(player),
                player.getPing(),
                spectator && !viewer.equals(player) ? GameMode.SPECTATOR : packetGameMode(player),
                spectatorDisplayName(viewer, player, spectator),
                null
            );
            if (viewer.equals(player)) {
                removeSelfSpectatorProfile(protocolManager, channel, player);
                if (spectator) {
                    sendSelfSpectatorProfile(protocolManager, channel, player);
                }
            }
            EnumSet<Action> actions = EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE, Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME);
            if (!viewer.equals(player) || !spectator) {
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

    private void sendSelfSpectatorProfile(ProtocolManager protocolManager, Object channel, Player player) {
        PlayerInfo fakeInfo = new PlayerInfo(
            userProfile(player, selfSpectatorProfileId(player)),
            true,
            player.getPing(),
            GameMode.SPECTATOR,
            playerListDisplayName(player),
            null
        );
        protocolManager.sendPacket(channel, new WrapperPlayServerPlayerInfoUpdate(
            EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE, Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME),
            List.of(fakeInfo)
        ));
    }

    private void removeSelfSpectatorProfile(ProtocolManager protocolManager, Object channel, Player player) {
        protocolManager.sendPacket(channel, new WrapperPlayServerPlayerInfoRemove(selfSpectatorProfileId(player)));
    }

    private void removeSelfSpectatorProfile(Player viewer, Player player) {
        try {
            var api = PacketEvents.getAPI();
            if (api == null) {
                return;
            }
            var protocolManager = api.getProtocolManager();
            Object channel = protocolManager.getChannel(viewer.getUniqueId());
            if (channel != null) {
                removeSelfSpectatorProfile(protocolManager, channel, player);
            }
        } catch (NoClassDefFoundError | RuntimeException ignored) {
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

    private Component spectatorDisplayName(Player viewer, Player player, boolean spectator) {
        if (!viewer.equals(player)) {
            return null;
        }
        return spectator ? null : playerListDisplayName(player);
    }

    private Component playerListDisplayName(Player player) {
        Component name = player.playerListName();
        GameModule game = plugin.moduleService().getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        GameTeam team = gamePlayer == null ? null : gamePlayer.getTeam();
        if (team == null) {
            return name;
        }
        return Component.empty()
            .append(team.prefix())
            .append(name)
            .append(team.suffix());
    }

    private UserProfile userProfile(Player player) {
        return userProfile(player, player.getUniqueId());
    }

    private UserProfile userProfile(Player player, UUID uuid) {
        List<TextureProperty> properties = new ArrayList<>();
        for (var property : player.getPlayerProfile().getProperties()) {
            properties.add(new TextureProperty(property.getName(), property.getValue(), property.getSignature()));
        }
        return new UserProfile(uuid, player.getName(), properties);
    }

    private UUID selfSpectatorProfileId(Player player) {
        return UUID.nameUUIDFromBytes((SELF_SPECTATOR_PROFILE_PREFIX + player.getUniqueId()).getBytes(StandardCharsets.UTF_8));
    }
}
