package com.donutsforlife11.donutgame.internal.game;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

public class SpectatorService {
    private final Donutgame plugin;
    private boolean packetEventsAvailable;

    public SpectatorService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        packetEventsAvailable = Bukkit.getPluginManager().isPluginEnabled("packetevents");
    }

    public void disable() {
        packetEventsAvailable = false;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player hidden : Bukkit.getOnlinePlayers()) {
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
            if (viewer.equals(player)) {
                removeRealPlayerEntry(viewer, player);
                sendFakeSpectatorEntry(viewer, player);
            } else {
                viewer.hidePlayer(plugin, player);
                sendFakeSpectatorEntry(viewer, player);
            }
        }
    }

    public void hideSpectator(GamePlayer spectator) {
        Player player = spectator == null ? null : spectator.bukkitPlayer();
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                removeFakeSpectatorEntry(viewer, player);
                sendRealPlayerEntry(viewer, player);
            } else {
                removeFakeSpectatorEntry(viewer, player);
                viewer.showPlayer(plugin, player);
            }
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
                    sendFakeSpectatorEntry(viewer, player);
                } else {
                    removeRealPlayerEntry(viewer, player);
                    sendFakeSpectatorEntry(viewer, player);
                }
            }
        }
    }

    public boolean isSpectator(Player player) {
        GameModule game = plugin.moduleService().getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        return gamePlayer != null && gamePlayer.isSpectator();
    }

    private void sendFakeSpectatorEntry(Player viewer, Player spectator) {
        if (!packetEventsAvailable || PacketEvents.getAPI() == null || viewer == null || spectator == null) {
            return;
        }
        try {
            UserProfile profile = new UserProfile(fakeId(spectator), spectator.getName());
            Component displayName = Component.text(spectator.getName()).decoration(TextDecoration.ITALIC, false);
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                spectator.getPing(),
                GameMode.SPECTATOR,
                displayName,
                null
            );
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
                ),
                List.of(entry)
            );
            PacketEvents.getAPI().getProtocolManager().sendPacket(viewer, packet);
        } catch (Throwable ignored) {
            packetEventsAvailable = false;
        }
    }

    private void sendRealPlayerEntry(Player viewer, Player player) {
        if (!packetEventsAvailable || PacketEvents.getAPI() == null || viewer == null || player == null) {
            return;
        }
        try {
            UserProfile profile = new UserProfile(player.getUniqueId(), player.getName());
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                player.getPing(),
                packetGameMode(player.getGameMode()),
                player.playerListName().decoration(TextDecoration.ITALIC, false),
                null
            );
            entry.setDisplayName(player.playerListName().decoration(TextDecoration.ITALIC, false));
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
                ),
                List.of(entry)
            );
            PacketEvents.getAPI().getProtocolManager().sendPacket(viewer, packet);
        } catch (Throwable ignored) {
            packetEventsAvailable = false;
        }
    }

    private void removeRealPlayerEntry(Player viewer, Player player) {
        if (!packetEventsAvailable || PacketEvents.getAPI() == null || viewer == null || player == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getProtocolManager().sendPacket(
                viewer,
                new WrapperPlayServerPlayerInfoRemove(player.getUniqueId())
            );
        } catch (Throwable ignored) {
            packetEventsAvailable = false;
        }
    }

    private void removeFakeSpectatorEntry(Player viewer, Player spectator) {
        if (!packetEventsAvailable || PacketEvents.getAPI() == null || viewer == null || spectator == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getProtocolManager().sendPacket(
                viewer,
                new WrapperPlayServerPlayerInfoRemove(fakeId(spectator))
            );
        } catch (Throwable ignored) {
            packetEventsAvailable = false;
        }
    }

    private UUID fakeId(Player player) {
        return UUID.nameUUIDFromBytes(("donutgame:spectator:" + player.getUniqueId()).getBytes(StandardCharsets.UTF_8));
    }

    private GameMode packetGameMode(org.bukkit.GameMode gameMode) {
        return switch (gameMode) {
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
    }
}
