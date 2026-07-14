package com.donutsforlife11.donutgame.api.ui.title;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.donutsforlife11.donutgame.Donutgame;

public class TitlePacketTracker {
    private final boolean available;
    private final Map<UUID, TitleState> states = new ConcurrentHashMap<>();

    public TitlePacketTracker(Donutgame plugin) {
        boolean available = false;
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
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
                        PacketType type = event.getPacketType();
                        if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
                            handleTitle(event);
                        } else if (type == PacketType.Play.Server.SET_TITLES_ANIMATION) {
                            handleAnimation(event);
                        } else if (type == PacketType.Play.Server.CLEAR_TITLES) {
                            forget(event.getPlayer());
                        }
                    }
                });
                available = true;
            } catch (Throwable throwable) {
                plugin.getLogger().warning("ProtocolLib title tracking is unavailable. Subtitles will use vanilla behavior.");
            }
        }
        this.available = available;
    }

    public boolean available() {
        return available;
    }

    public boolean hasActiveTitle(Player player) {
        UUID uuid = player.getUniqueId();
        TitleState state = states.get(uuid);

        if (state == null || !state.hasTitle) {
            return false;
        }

        if (System.currentTimeMillis() >= state.expiresAtMillis) {
            states.remove(uuid);
            return false;
        }

        return true;
    }

    private void handleTitle(PacketEvent event) {
        TitleState state = state(event.getPlayer());
        state.hasTitle = true;
        state.refresh();
    }
    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }
    private TitleState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new TitleState());
    }
    private void handleAnimation(PacketEvent event) {
        TitleState state = state(event.getPlayer());

        state.fadeInTicks = event.getPacket().getIntegers().read(0);
        state.stayTicks = event.getPacket().getIntegers().read(1);
        state.fadeOutTicks = event.getPacket().getIntegers().read(2);

        if (state.hasTitle) {
            state.refresh();
        }
    }

    private static class TitleState {
        private static final long TICK_MILLIS = 50L;
        boolean hasTitle;

        int fadeInTicks = 10;
        int stayTicks = 70;
        int fadeOutTicks = 20;

        long expiresAtMillis;

        void refresh() {
            expiresAtMillis = System.currentTimeMillis() + totalMillis();
        }
        long totalMillis() {
            return (fadeInTicks + stayTicks + fadeOutTicks) * TICK_MILLIS;
        }
    }
}
