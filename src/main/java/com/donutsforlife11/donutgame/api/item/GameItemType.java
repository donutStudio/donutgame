package com.donutsforlife11.donutgame.api.item;

import java.util.Objects;

import org.bukkit.Material;

public final class GameItemType {
    private final Material material;

    private GameItemType(Material material) {
        this.material = Objects.requireNonNull(material, "material");
        if (!material.isItem()) {
            throw new IllegalArgumentException(material + " is not an item.");
        }
    }

    public static GameItemType of(Material material) {
        return new GameItemType(material);
    }

    public Material material() {
        return material;
    }

    public GameItem create() {
        return GameItem.of(material);
    }
}
