package com.donutsforlife11.donutgame.internal.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import fr.skytasul.glowingentities.GlowingEntities;
import net.kyori.adventure.text.format.NamedTextColor;

@SuppressWarnings("deprecation")
public class GlowService {
    private static final byte ON_FIRE_FLAG = 0x01;
    private static final byte CROUCHING_FLAG = 0x02;
    private static final byte SPRINTING_FLAG = 0x08;
    private static final byte SWIMMING_FLAG = 0x10;
    private static final byte INVISIBLE_FLAG = 0x20;
    private static final byte GLOWING_FLAG = 0x40;
    private static final byte GLIDING_FLAG = (byte) 0x80;

    private final Donutgame plugin;
    private final Map<UUID, GlowState> glowStates = new HashMap<>();
    private GlowingEntities glowingEntities;
    private boolean packetGlowAvailable;

    public GlowService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        packetGlowAvailable = true;
        try {
            glowingEntities = new GlowingEntities(plugin);
        } catch (Throwable ignored) {
            glowingEntities = null;
        }
    }

    public void disable() {
        for (GlowState state : glowStates.values()) {
            Entity entity = Bukkit.getEntity(state.entityId());
            if (entity != null) {
                entity.setGlowing(false);
            }
        }
        if (glowingEntities != null) {
            try {
                glowingEntities.disable();
            } catch (Throwable ignored) {
            }
        }
        glowingEntities = null;
        packetGlowAvailable = false;
        glowStates.clear();
    }

    public boolean setGlowing(Entity entity, boolean glowing, NamedTextColor color) {
        if (entity == null) {
            return false;
        }
        GlowState state = glowStates.computeIfAbsent(entity.getUniqueId(), GlowState::new);
        state.color(color);
        state.packetOnly(false);
        state.global(glowing);
        state.viewerSupplier(null);
        return apply(state, entity, true);
    }

    public boolean setGlowing(Entity entity, boolean glowing, Collection<GamePlayer> viewers, NamedTextColor color) {
        if (entity == null) {
            return false;
        }
        GlowState state = glowStates.computeIfAbsent(entity.getUniqueId(), GlowState::new);
        state.color(color);
        state.packetOnly(false);
        state.global(false);
        state.viewerSupplier(glowing ? () -> viewers : null);
        return apply(state, entity, true);
    }

    public boolean setGlowing(Entity entity, boolean glowing, Supplier<Collection<GamePlayer>> viewers, NamedTextColor color) {
        if (entity == null) {
            return false;
        }
        GlowState state = glowStates.computeIfAbsent(entity.getUniqueId(), GlowState::new);
        state.color(color);
        state.packetOnly(false);
        state.global(false);
        state.viewerSupplier(glowing ? viewers : null);
        return apply(state, entity, true);
    }

    public boolean setPacketGlowing(Entity entity, boolean glowing, Supplier<Collection<GamePlayer>> viewers, NamedTextColor color) {
        if (entity == null) {
            return false;
        }
        GlowState state = glowStates.computeIfAbsent(entity.getUniqueId(), GlowState::new);
        state.color(null);
        state.packetOnly(true);
        state.global(false);
        state.viewerSupplier(glowing ? viewers : null);
        return apply(state, entity, false);
    }

    public void setGlowColor(Entity entity, NamedTextColor color) {
        if (entity == null) {
            return;
        }
        GlowState state = glowStates.computeIfAbsent(entity.getUniqueId(), GlowState::new);
        state.color(color);
        state.packetOnly(false);
        apply(state, entity, true);
    }

    public boolean packetGlowAvailable() {
        try {
            return packetGlowAvailable && PacketEvents.getAPI() != null;
        } catch (NoClassDefFoundError | RuntimeException exception) {
            return false;
        }
    }

    private boolean apply(GlowState state, Entity entity, boolean fallbackToVanilla) {
        clearPacketGlow(entity, state.appliedViewers(), state.appliedPacketOnly());
        state.appliedViewers().clear();
        state.appliedPacketOnly(state.packetOnly());

        if (state.global()) {
            entity.setGlowing(true);
            return applyPacketGlow(entity, Bukkit.getOnlinePlayers(), state, fallbackToVanilla);
        }

        entity.setGlowing(false);
        Collection<GamePlayer> viewers = state.viewers();
        if (viewers == null || viewers.isEmpty()) {
            return true;
        }
        if (!packetGlowAvailable()) {
            entity.setGlowing(fallbackToVanilla);
            return fallbackToVanilla;
        }
        Set<Player> bukkitViewers = new HashSet<>();
        for (GamePlayer viewer : viewers) {
            Player player = viewer == null ? null : viewer.bukkitPlayer();
            if (player != null && player.isOnline()) {
                bukkitViewers.add(player);
            }
        }
        return applyPacketGlow(entity, bukkitViewers, state, fallbackToVanilla);
    }

    private boolean applyPacketGlow(Entity entity, Collection<? extends Player> viewers, GlowState state, boolean fallbackToVanilla) {
        if (state.packetOnly() && !packetGlowAvailable()) {
            return fallbackToVanilla;
        }
        if (!state.packetOnly() && glowingEntities == null) {
            return fallbackToVanilla;
        }
        ChatColor chatColor = chatColor(state.color());
        for (Player viewer : viewers) {
            try {
                if (state.packetOnly()) {
                    sendMetadataGlow(entity, viewer, true);
                } else if (chatColor == null) {
                    glowingEntities.setGlowing(entity, viewer);
                } else {
                    glowingEntities.setGlowing(entity, viewer, chatColor);
                }
                state.appliedViewers().add(viewer.getUniqueId());
            } catch (Throwable ignored) {
                packetGlowAvailable = false;
                clearPacketGlow(entity, state.appliedViewers(), state.appliedPacketOnly());
                state.appliedViewers().clear();
                if (state.global() || fallbackToVanilla) {
                    entity.setGlowing(true);
                } else {
                    entity.setGlowing(false);
                }
                return false;
            }
        }
        return true;
    }

    private void clearPacketGlow(Entity entity, Collection<UUID> viewers, boolean packetOnly) {
        if (!packetOnly && glowingEntities == null) {
            return;
        }
        for (UUID viewerId : Set.copyOf(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                continue;
            }
            try {
                if (packetOnly) {
                    sendMetadataGlow(entity, viewer, false);
                } else {
                    glowingEntities.unsetGlowing(entity, viewer);
                }
            } catch (Throwable ignored) {
                packetGlowAvailable = false;
            }
        }
    }

    private void sendMetadataGlow(Entity entity, Player viewer, boolean glowing) {
        var api = PacketEvents.getAPI();
        if (api == null) {
            throw new IllegalStateException("PacketEvents is not available.");
        }
        Object channel = api.getProtocolManager().getChannel(viewer.getUniqueId());
        if (channel == null) {
            throw new IllegalStateException("PacketEvents channel is not available for " + viewer.getName());
        }
        byte flags = sharedFlags(entity);
        flags = glowing ? (byte) (flags | GLOWING_FLAG) : (byte) (flags & ~GLOWING_FLAG);
        api.getProtocolManager().sendPacket(channel, new WrapperPlayServerEntityMetadata(
            entity.getEntityId(),
            List.of(new EntityData<>(0, EntityDataTypes.BYTE, flags))
        ));
    }

    private byte sharedFlags(Entity entity) {
        byte flags = 0;
        if (entity.getFireTicks() > 0 || entity.isVisualFire()) {
            flags |= ON_FIRE_FLAG;
        }
        if (entity instanceof Player player) {
            if (player.isSneaking()) {
                flags |= CROUCHING_FLAG;
            }
            if (player.isSprinting()) {
                flags |= SPRINTING_FLAG;
            }
            if (player.isSwimming()) {
                flags |= SWIMMING_FLAG;
            }
        }
        if (entity.isInvisible()) {
            flags |= INVISIBLE_FLAG;
        }
        if (entity.isGlowing()) {
            flags |= GLOWING_FLAG;
        }
        if (entity instanceof LivingEntity livingEntity && livingEntity.isGliding()) {
            flags |= GLIDING_FLAG;
        }
        return flags;
    }

    private ChatColor chatColor(NamedTextColor color) {
        if (color == null) {
            return null;
        }
        return switch (color.toString()) {
            case "black" -> ChatColor.BLACK;
            case "dark_blue" -> ChatColor.DARK_BLUE;
            case "dark_green" -> ChatColor.DARK_GREEN;
            case "dark_aqua" -> ChatColor.DARK_AQUA;
            case "dark_red" -> ChatColor.DARK_RED;
            case "dark_purple" -> ChatColor.DARK_PURPLE;
            case "gold" -> ChatColor.GOLD;
            case "gray" -> ChatColor.GRAY;
            case "dark_gray" -> ChatColor.DARK_GRAY;
            case "blue" -> ChatColor.BLUE;
            case "green" -> ChatColor.GREEN;
            case "aqua" -> ChatColor.AQUA;
            case "red" -> ChatColor.RED;
            case "light_purple" -> ChatColor.LIGHT_PURPLE;
            case "yellow" -> ChatColor.YELLOW;
            default -> ChatColor.WHITE;
        };
    }

    private static final class GlowState {
        private final UUID entityId;
        private final Set<UUID> appliedViewers = new HashSet<>();
        private Supplier<Collection<GamePlayer>> viewerSupplier;
        private NamedTextColor color;
        private boolean global;
        private boolean packetOnly;
        private boolean appliedPacketOnly;

        private GlowState(UUID entityId) {
            this.entityId = entityId;
        }

        private UUID entityId() {
            return entityId;
        }

        private boolean global() {
            return global;
        }

        private void global(boolean global) {
            this.global = global;
        }

        private Collection<GamePlayer> viewers() {
            return viewerSupplier == null ? null : viewerSupplier.get();
        }

        private void viewerSupplier(Supplier<Collection<GamePlayer>> viewerSupplier) {
            this.viewerSupplier = viewerSupplier;
        }

        private NamedTextColor color() {
            return color;
        }

        private void color(NamedTextColor color) {
            this.color = color;
        }

        private Set<UUID> appliedViewers() {
            return appliedViewers;
        }

        private boolean packetOnly() {
            return packetOnly;
        }

        private void packetOnly(boolean packetOnly) {
            this.packetOnly = packetOnly;
        }

        private boolean appliedPacketOnly() {
            return appliedPacketOnly;
        }

        private void appliedPacketOnly(boolean appliedPacketOnly) {
            this.appliedPacketOnly = appliedPacketOnly;
        }
    }
}
