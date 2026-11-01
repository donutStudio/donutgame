package com.donutsforlife11.donutgame.api.entity.npc;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.BlockType;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.BlockTypeTagKeys;
import io.papermc.paper.registry.tag.TagKey;

import com.donutsforlife11.donutgame.api.object.GameBlockType;

public final class PathfindSettings {
    // Material keys give GameBlockType value semantics without changing that shared API.
    final Set<Material> crossable = EnumSet.noneOf(Material.class);
    final Set<Material> walkable = EnumSet.noneOf(Material.class);
    private double radius = 32.0;

    PathfindSettings() {
        Set<Material> hazards = EnumSet.of(Material.LAVA, Material.WATER, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW, Material.COBWEB,
            Material.POINTED_DRIPSTONE);
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
        for (var tag : List.of(BlockTypeTagKeys.FIRE, BlockTypeTagKeys.CAMPFIRES, BlockTypeTagKeys.PORTALS)) {
            registry.getTagValues(tag).forEach(block -> hazards.add(Material.matchMaterial(block.key().asString())));
        }
        for (BlockType block : registry) {
            Material material = Material.matchMaterial(block.key().asString());
            if (material == null || hazards.contains(material)) {
                continue;
            }
            // Whitelists describe safety, while collision shapes describe physical clearance.
            // Open doors and the empty portion of slabs remain traversable by default.
            crossable.add(material);
            if (material.isSolid()) {
                walkable.add(material);
            }
        }
        addWalkableTags(BlockTypeTagKeys.WOOL_CARPETS);
        walkable.add(Material.MOSS_CARPET);
        walkable.add(Material.PALE_MOSS_CARPET);
        walkable.add(Material.SNOW);
    }

    public void setRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Pathfinding radius must be finite and positive.");
        }
        this.radius = radius;
    }

    public double radius() {
        return radius;
    }

    public void addCrossableBlocks(GameBlockType... blocks) {
        addCrossableBlocks(Arrays.asList(blocks));
    }

    public void addCrossableBlocks(Collection<GameBlockType> blocks) {
        blocks.forEach(block -> crossable.add(block.material()));
    }

    public void removeCrossableBlocks(GameBlockType... blocks) {
        removeCrossableBlocks(Arrays.asList(blocks));
    }

    public void removeCrossableBlocks(Collection<GameBlockType> blocks) {
        blocks.forEach(block -> crossable.remove(block.material()));
    }

    public void addWalkableBlocks(GameBlockType... blocks) {
        addWalkableBlocks(Arrays.asList(blocks));
    }

    public void addWalkableBlocks(Collection<GameBlockType> blocks) {
        blocks.forEach(block -> walkable.add(block.material()));
    }

    public void removeWalkableBlocks(GameBlockType... blocks) {
        removeWalkableBlocks(Arrays.asList(blocks));
    }

    public void removeWalkableBlocks(Collection<GameBlockType> blocks) {
        blocks.forEach(block -> walkable.remove(block.material()));
    }

    public Collection<GameBlockType> crossableBlocks() {
        return crossable.stream().map(GameBlockType::of).toList();
    }

    public Collection<GameBlockType> walkableBlocks() {
        return walkable.stream().map(GameBlockType::of).toList();
    }

    /** Tags are expanded when applied; later block additions/removals override them. */
    @SafeVarargs
    public final void addCrossableTags(TagKey<BlockType>... tags) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
        for (var tag : tags) {
            registry.getTagValues(tag).forEach(block -> crossable.add(Material.matchMaterial(block.key().asString())));
        }
    }

    @SafeVarargs
    public final void removeCrossableTags(TagKey<BlockType>... tags) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
        for (var tag : tags) {
            registry.getTagValues(tag).forEach(block -> crossable.remove(Material.matchMaterial(block.key().asString())));
        }
    }

    @SafeVarargs
    public final void addWalkableTags(TagKey<BlockType>... tags) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
        for (var tag : tags) {
            registry.getTagValues(tag).forEach(block -> walkable.add(Material.matchMaterial(block.key().asString())));
        }
    }

    @SafeVarargs
    public final void removeWalkableTags(TagKey<BlockType>... tags) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
        for (var tag : tags) {
            registry.getTagValues(tag).forEach(block -> walkable.remove(Material.matchMaterial(block.key().asString())));
        }
    }
}

