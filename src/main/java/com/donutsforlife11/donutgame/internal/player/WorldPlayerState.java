package com.donutsforlife11.donutgame.internal.player;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class WorldPlayerState {
    public ItemStack[] inventory;
    public ItemStack[] armor;
    public ItemStack offhand;
    public ItemStack[] enderChest;

    public double health;
    public int foodLevel;
    public float saturation;
    public float exhaustion;

    public int level;
    public float exp;
    public int totalExperience;

    public GameMode gameMode;
    public List<PotionEffect> potionEffects;
}
