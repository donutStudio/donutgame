package com.donutsforlife11.donutgame.api.border;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.BorderManager.BorderShape;

public class GameBorder {
    private final BorderManager borderManager;
    private final BorderShape shape;
    private Location center;
    private Vector dimensions;
    private double damageAmount = 1.5;
    private int damageInterval = 4;

    public GameBorder(BorderManager borderManager, BorderShape shape, Location center, Vector dimensions) {
        this.shape = shape;
        this.center = center;
        this.dimensions = dimensions;
        this.borderManager = borderManager;
    }

    public GameBorder setDamage(double amount, int interval) {
        this.damageAmount = amount;
        this.damageInterval = interval;
        return this;
    }

    public BorderShape getShape() {
        return shape;
    }
    public Location getCenter() {
        return center;
    }
    public Vector getDimensions() {
        return dimensions;
    }

    void deleteBorder() {
        borderManager.deleteBorder(this);
    }
}
