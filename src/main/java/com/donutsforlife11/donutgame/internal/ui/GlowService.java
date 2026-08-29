package com.donutsforlife11.donutgame.internal.ui;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import fr.skytasul.glowingentities.GlowingEntities;
import net.kyori.adventure.text.format.NamedTextColor;

@SuppressWarnings("deprecation")
public class GlowService {
    private final Plugin plugin;
    private final Set<GlowKey> active = ConcurrentHashMap.newKeySet();
    private GlowingEntities glowingEntities;

    public GlowService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        glowingEntities = new GlowingEntities(plugin);
    }

    public void disable() {
        clearAppliedCache();
        if (glowingEntities != null) glowingEntities.disable();
        glowingEntities = null;
    }

    public void glowEntity(Entity entity, Collection<Player> viewers, NamedTextColor color) {
        if (entity == null || viewers == null || glowingEntities == null) return;
        ChatColor chatColor = chatColor(color);
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline()) continue;
            try {
                if (chatColor == null) glowingEntities.setGlowing(entity, viewer);
                else glowingEntities.setGlowing(entity, viewer, chatColor);
                active.add(new GlowKey(entity.getUniqueId(), viewer.getUniqueId()));
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    public void clearEntityGlow(Entity entity, Collection<Player> viewers) {
        if (entity == null || viewers == null || glowingEntities == null) return;
        for (Player viewer : viewers) {
            if (viewer == null) continue;
            GlowKey key = new GlowKey(entity.getUniqueId(), viewer.getUniqueId());
            if (!active.remove(key)) continue;
            try {
                glowingEntities.unsetGlowing(entity, viewer);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    public void clearAppliedCache(UUID viewerId) {
        active.removeIf(key -> key.viewerId().equals(viewerId));
    }

    public void clearAppliedCache() {
        active.clear();
    }

    private ChatColor chatColor(NamedTextColor color) {
        if (color == null) return null;
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

    private record GlowKey(UUID entityId, UUID viewerId) {
    }
}
