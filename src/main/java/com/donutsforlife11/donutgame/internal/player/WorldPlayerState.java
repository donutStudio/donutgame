package com.donutsforlife11.donutgame.internal.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class WorldPlayerState {
    public ItemStack[] inventory;
    public ItemStack[] armor;
    public ItemStack offhand;
    public ItemStack[] enderChest;

    public Set<AttributeState> attributes = new HashSet<>();

    public double health;
    public int foodLevel;
    public float saturation;
    public float exhaustion;

    public int level;
    public float exp;
    public int totalExperience;

    public GameMode gameMode;
    public List<PotionEffect> potionEffects;

    public record AttributeState(Attribute attribute, double base, List<AttributeModifier> modifiers) {
        public AttributeState {
            modifiers = List.copyOf(modifiers);
        }
    }
}
