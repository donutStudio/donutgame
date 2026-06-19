package com.donutsforlife11.donutgame.api.ui;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class GameGlow {
    public sealed interface GlowTarget permits EntityTarget, BlockTarget {}
    public record EntityTarget(Entity entity) implements GlowTarget {}
    public record BlockTarget(Block block) implements GlowTarget {}

    private final UIManager uiManager;
    private boolean removed;

    private GlowTarget target;
    private Audience audience;
    private TextColor color;
    private int priority;

    public GameGlow(UIManager uiManager) {
        this.uiManager = uiManager;
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (Player player : uiManager.resolvePlayers(audience)) {
            try {
                switch (target) {
                    case EntityTarget(Entity entity) -> {
                        uiManager.glowingEntities().unsetGlowing(entity, player);
                    }
                    case BlockTarget(Block block) -> {
                        uiManager.glowingBlocks().unsetGlowing(block, player);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        uiManager.remove(this);
    }

    public GameGlow setTarget(Entity entity) {
        ensureUsable();
        return setTarget(new EntityTarget(entity));
    }
    public GameGlow setTarget(Block block) {
        ensureUsable();
        return setTarget(new BlockTarget(block));
    }
    private GameGlow setTarget(GlowTarget target) {
        this.target = target;
        applyGlow();
        return this;
    }
    public GameGlow setAudience(Audience audience) {
        ensureUsable();
        this.audience = audience;
        applyGlow();
        return this;
    }
    public GameGlow setColor(TextColor color) {
        ensureUsable();
        this.color = color;
        applyGlow();
        return this;
    }
    public GameGlow setPriority(int priority) {
        ensureUsable();
        this.priority = priority;
        applyGlow();
        return this;
    }

    public GlowTarget getTarget() {
        ensureUsable();
        return target;
    }
    public Audience audience() {
        ensureUsable();
        return audience;
    }
    public TextColor color() {
        ensureUsable();
        return color;
    }
    public int priority() {
        ensureUsable();
        return priority;
    }

    @SuppressWarnings("deprecation")
    private void applyGlow() {
        for (Player player : uiManager.resolvePlayers(audience)) {
            try {
                NamedTextColor nearestNamedColor = NamedTextColor.nearestTo(color);
                switch (target) {
                    case EntityTarget(Entity entity) -> {
                        uiManager.glowingEntities().setGlowing(entity, player, ChatColor.valueOf(nearestNamedColor.toString().toUpperCase()));
                    }
                    case BlockTarget(Block block) -> {
                        uiManager.glowingBlocks().setGlowing(block, player, ChatColor.valueOf(nearestNamedColor.toString().toUpperCase()));
                    }
                }
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    private void ensureUsable() {
        if (removed) {
            throw new IllegalStateException("Glow has already been removed.");
        }
    }
}
