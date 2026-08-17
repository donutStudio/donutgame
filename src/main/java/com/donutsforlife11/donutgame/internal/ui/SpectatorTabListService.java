package com.donutsforlife11.donutgame.internal.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.ModuleService;

public class SpectatorTabListService {
    private final Plugin plugin;
    private final ModuleService moduleService;
    private PacketListener listener;
    private boolean warningLogged;

    public SpectatorTabListService(Plugin plugin, ModuleService moduleService) {
        this.plugin = plugin;
        this.moduleService = moduleService;
    }

    public void enable() {
        if (listener != null) return;
        listener = new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    rewriteSpectatorGameModes(event);
                } catch (RuntimeException | LinkageError exception) {
                    warnOnce(exception);
                }
            }
        };
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(listener);
        } catch (RuntimeException | LinkageError exception) {
            listener = null;
            warnOnce(exception);
        }
    }

    public void disable() {
        if (listener == null) return;
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(listener);
        } catch (RuntimeException | LinkageError ignored) {
        }
        listener = null;
    }

    private void rewriteSpectatorGameModes(PacketEvent event) {
        GameModule game = game(event.getPlayer());
        if (game == null) return;

        Set<EnumWrappers.PlayerInfoAction> actions = event.getPacket().getPlayerInfoActions().readSafely(0);
        if (actions != null
            && !actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER)
            && !actions.contains(EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE)) {
            return;
        }

        var entryLists = event.getPacket().getPlayerInfoDataLists();
        int entryIndex = entryLists.size() > 1 ? 1 : 0;
        List<PlayerInfoData> entries = entryLists.size() == 0 ? null : entryLists.readSafely(entryIndex);
        if ((entries == null || entries.isEmpty()) && entryIndex != 0) {
            entries = entryLists.readSafely(0);
            entryIndex = 0;
        }
        if (entries == null || entries.isEmpty()) return;

        List<PlayerInfoData> rewritten = null;
        for (int i = 0; i < entries.size(); i++) {
            PlayerInfoData entry = entries.get(i);
            GamePlayer subject = game.playerManager().getPlayer(entry.getProfileId());
            if (subject == null || !subject.isSpectator() || subject.uuid().equals(event.getPlayer().getUniqueId())) {
                continue;
            }
            if (rewritten == null) rewritten = new ArrayList<>(entries);
            rewritten.set(i, new PlayerInfoData(
                entry.getProfileId(),
                entry.getLatency(),
                entry.isListed(),
                EnumWrappers.NativeGameMode.SPECTATOR,
                entry.getProfile(),
                entry.getDisplayName(),
                entry.isShowHat(),
                entry.getListOrder(),
                entry.getRemoteChatSessionData()
            ));
        }

        if (rewritten != null) {
            entryLists.write(entryIndex, rewritten);
        }
    }

    private GameModule game(Player player) {
        if (player == null) return null;
        try {
            return moduleService.getGameOfPlayer(player);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void warnOnce(Throwable exception) {
        if (warningLogged) return;
        warningLogged = true;
        plugin.getLogger().log(Level.WARNING, "Could not enable spectator tab-list translucency. ProtocolLib may not support this server build.", exception);
    }
}
