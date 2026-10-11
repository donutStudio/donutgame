package com.donutsforlife11.donutgame.api.object;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public final class GameBlockType {
    private final Material material;

    private GameBlockType(Material material) {
        this.material = Objects.requireNonNull(material, "material");
        if (!material.isBlock() && material != Material.AIR) {
            throw new IllegalArgumentException(material + " is not a block.");
        }
    }

    public static GameBlockType of(Material material) {
        return new GameBlockType(material);
    }

    public Material material() {
        return material;
    }

    public BlockData createBlockData() {
        return material.createBlockData();
    }
}
