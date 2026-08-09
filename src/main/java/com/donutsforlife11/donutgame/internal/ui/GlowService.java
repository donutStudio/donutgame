package com.donutsforlife11.donutgame.internal.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.ui.GameGlow;
import fr.skytasul.glowingentities.GlowingEntities;

import net.kyori.adventure.text.format.NamedTextColor;

public class GlowService {
    private final Plugin plugin;
    private GlowingEntities glowingEntities;
    private boolean initializationAttempted;
    private boolean available;

    private final Map<GlowKey, NamedTextColor> activeGlows = new HashMap<>();

    public GlowService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        initializeIfNeeded();
    }

    public void disable() {
        activeGlows.clear();
        if (glowingEntities != null) {
            glowingEntities.disable();
            glowingEntities = null;
        }
        available = false;
        initializationAttempted = false;
    }

    @SuppressWarnings("deprecation")
    public void glowEntity(
        Entity entity,
        Collection<Player> viewers,
        NamedTextColor color
    ) {
        if (!isAvailable()) {
            return;
        }

        ChatColor chatColor = toChatColor(color);

        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }

            GlowKey key = new GlowKey(
                entity.getUniqueId(),
                viewer.getUniqueId()
            );

            NamedTextColor currentColor = activeGlows.get(key);

            /*
            * Client already has exactly the glow we want.
            * Don't send another packet.
            */
            if (color.equals(currentColor)) {
                continue;
            }

            try {
                glowingEntities.setGlowing(entity, viewer, chatColor);
                activeGlows.put(key, color);
            } catch (ReflectiveOperationException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void clearEntityGlow(
        Entity entity,
        Collection<Player> viewers
    ) {
        if (!isAvailable()) {
            return;
        }

        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }

            GlowKey key = new GlowKey(
                entity.getUniqueId(),
                viewer.getUniqueId()
            );

            /*
            * We never told this viewer that this entity was glowing,
            * so there's nothing to clear.
            */
            if (!activeGlows.containsKey(key)) {
                continue;
            }

            try {
                glowingEntities.unsetGlowing(entity, viewer);
                activeGlows.remove(key);
            } catch (ReflectiveOperationException exception) {
                exception.printStackTrace();
            }
        }
    }

    public GameGlow glowBlock(GameWorld world, GameLocation location, BlockData blockData, Collection<Player> viewers, NamedTextColor color) {
        if (!isAvailable()) {
            return new GameGlow(() -> {
            });
        }
        BlockDisplay display = world.bukkitWorld().spawn(location.toBukkit(world.bukkitWorld()).toCenterLocation(), BlockDisplay.class, spawned -> {
            spawned.setBlock(blockData);
            spawned.setGlowing(true);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setVisibleByDefault(false);
            spawned.setTransformation(new Transformation(
                new Vector3f(-0.005f, -0.005f, -0.005f),
                new org.joml.Quaternionf(),
                new Vector3f(1.01f, 1.01f, 1.01f),
                new org.joml.Quaternionf()
            ));
        });
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            viewer.showEntity(plugin, display);
        }
        glowEntity(display, viewers, color);
        return new GameGlow(() -> {
            clearEntityGlow(display, viewers);
            if (display.isValid()) {
                display.remove();
            }
        });
    }

    public boolean isAvailable() {
        initializeIfNeeded();
        return available;
    }

    private void initializeIfNeeded() {
        if (initializationAttempted) {
            return;
        }
        initializationAttempted = true;
        try {
            glowingEntities = new GlowingEntities(plugin);
            available = true;
        } catch (Throwable throwable) {
            available = false;
            plugin.getLogger().warning("GlowingEntities is unavailable on this server build. Glow features will be disabled.");
            plugin.getLogger().warning(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private ChatColor toChatColor(NamedTextColor color) {
        if (color == NamedTextColor.BLACK) {
            return ChatColor.BLACK;
        }
        if (color == NamedTextColor.DARK_BLUE) {
            return ChatColor.DARK_BLUE;
        }
        if (color == NamedTextColor.DARK_GREEN) {
            return ChatColor.DARK_GREEN;
        }
        if (color == NamedTextColor.DARK_AQUA) {
            return ChatColor.DARK_AQUA;
        }
        if (color == NamedTextColor.DARK_RED) {
            return ChatColor.DARK_RED;
        }
        if (color == NamedTextColor.DARK_PURPLE) {
            return ChatColor.DARK_PURPLE;
        }
        if (color == NamedTextColor.GOLD) {
            return ChatColor.GOLD;
        }
        if (color == NamedTextColor.GRAY) {
            return ChatColor.GRAY;
        }
        if (color == NamedTextColor.DARK_GRAY) {
            return ChatColor.DARK_GRAY;
        }
        if (color == NamedTextColor.BLUE) {
            return ChatColor.BLUE;
        }
        if (color == NamedTextColor.GREEN) {
            return ChatColor.GREEN;
        }
        if (color == NamedTextColor.AQUA) {
            return ChatColor.AQUA;
        }
        if (color == NamedTextColor.RED) {
            return ChatColor.RED;
        }
        if (color == NamedTextColor.LIGHT_PURPLE) {
            return ChatColor.LIGHT_PURPLE;
        }
        if (color == NamedTextColor.YELLOW) {
            return ChatColor.YELLOW;
        }
        return ChatColor.WHITE;
    }

    private record GlowKey(UUID entityId, UUID viewerId) {
    }
}
