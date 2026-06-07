package com.donutsforlife11.donutgame.api.ui.title;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

public final class TitlePacketTracker {
    private final boolean available;
    private final Map<UUID, TitleState> states = new ConcurrentHashMap<>();

    public TitlePacketTracker(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            available = false;
            return;
        }

        boolean hooked = false;

        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                    plugin,
                    ListenerPriority.MONITOR,
                    PacketType.Play.Server.SET_TITLE_TEXT,
                    PacketType.Play.Server.SET_TITLES_ANIMATION,
                    PacketType.Play.Server.CLEAR_TITLES
            ) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    UUID uuid = player.getUniqueId();
                    PacketType type = event.getPacketType();

                    if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
                        TitleState state = states.computeIfAbsent(uuid, ignored -> new TitleState());
                        state.hasTitle = true;
                        state.expiresAtMillis = System.currentTimeMillis() + state.totalMillis();
                        return;
                    }

                    if (type == PacketType.Play.Server.SET_TITLES_ANIMATION) {
                        TitleState state = states.computeIfAbsent(uuid, ignored -> new TitleState());

                        state.fadeInTicks = event.getPacket().getIntegers().read(0);
                        state.stayTicks = event.getPacket().getIntegers().read(1);
                        state.fadeOutTicks = event.getPacket().getIntegers().read(2);

                        if (state.hasTitle) {
                            state.expiresAtMillis = System.currentTimeMillis() + state.totalMillis();
                        }

                        return;
                    }

                    if (type == PacketType.Play.Server.CLEAR_TITLES) {
                        states.remove(uuid);
                    }
                }
            });

            hooked = true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("ProtocolLib title tracking is unavailable. Subtitles will use vanilla behavior.");
        }

        available = hooked;
    }

    public boolean available() {
        return available;
    }

    public boolean hasActiveTitle(Player player) {
        TitleState state = states.get(player.getUniqueId());

        if (state == null || !state.hasTitle) {
            return false;
        }

        if (state.expiresAtMillis <= System.currentTimeMillis()) {
            states.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    private static final class TitleState {
        boolean hasTitle;

        int fadeInTicks = 10;
        int stayTicks = 70;
        int fadeOutTicks = 20;

        long expiresAtMillis;

        long totalMillis() {
            return (fadeInTicks + stayTicks + fadeOutTicks) * 50L;
        }
    }
}