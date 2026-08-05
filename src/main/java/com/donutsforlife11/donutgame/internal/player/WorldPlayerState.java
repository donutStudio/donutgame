package com.donutsforlife11.donutgame.internal.player;

import java.util.Collection;
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

    public AttributeState newAttributeState(Attribute attribute, double base, Collection<AttributeModifier> modifiers) {
        return new AttributeState(attribute, base, modifiers);
    }
    public static class AttributeState {
        private final Attribute attribute;
        private double base;
        private final Collection<AttributeModifier> modifiers;
        public AttributeState(Attribute attribute, double base, Collection<AttributeModifier> modifiers) {
            this.attribute = attribute;
            this.base = base;
            this.modifiers = new HashSet<>(modifiers);
        }
        public void setBase(double base) {
            this.base = base;
        }

        public Attribute attribute() {
            return attribute;
        }
        public double base() {
            return base;
        }
        public Collection<AttributeModifier> modifiers() {
            return modifiers;
        }
    }
}
