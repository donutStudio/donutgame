package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import com.donutsforlife11.donutgame.api.object.GameBlockType;

public final class GameBlock {
    private final GameWorld world;
    private final long generation;
    private final int x;
    private final int y;
    private final int z;

    GameBlock(GameWorld world, long generation, int x, int y, int z) {
        this.world = Objects.requireNonNull(world, "world");
        this.generation = generation;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    GameBlock(GameWorld world, long generation, Block block) {
        this(world, generation, block.getX(), block.getY(), block.getZ());
    }

    public GameWorld world() {
        return world;
    }

    public GameLocation location() {
        return new GameLocation(world, x, y, z);
    }

    public Material type() {
        return block().getType();
    }

    public boolean is(Material material) {
        return material != null && type() == material;
    }

    public boolean is(GameBlockType type) {
        return type != null && is(type.material());
    }

    public void setType(Material material) {
        block().setType(Objects.requireNonNull(material, "material"), false);
    }

    public void setType(GameBlockType type) {
        setType(Objects.requireNonNull(type, "type").material());
    }

    public BlockData blockData() {
        return block().getBlockData().clone();
    }

    public void setBlockData(BlockData data) {
        block().setBlockData(Objects.requireNonNull(data, "data").clone(), false);
    }

    public <S extends BlockState> void editState(Class<S> stateType, Consumer<? super S> editor) {
        Objects.requireNonNull(stateType, "stateType");
        Objects.requireNonNull(editor, "editor");
        BlockState state = block().getState();
        if (!stateType.isInstance(state)) {
            throw new IllegalStateException("Block at " + x + ", " + y + ", " + z + " is not a " + stateType.getSimpleName() + ".");
        }
        editor.accept(stateType.cast(state));
        state.update(true, false);
    }

    public void clearInventory() {
        editState(Container.class, container -> container.getInventory().clear());
    }

    public void setLootTable(LootTable lootTable) {
        setLootTable(lootTable, 0L);
    }

    public void setLootTable(LootTable lootTable, long seed) {
        editState(BlockState.class, state -> {
            if (state instanceof Container container) {
                container.getInventory().clear();
            }
            if (!(state instanceof Lootable lootable)) {
                throw new IllegalStateException("Block at " + x + ", " + y + ", " + z + " cannot use a loot table.");
            }
            lootable.setLootTable(lootTable);
            lootable.setSeed(seed);
        });
    }

    private Block block() {
        return world.requireBukkitWorld(generation).getBlockAt(x, y, z);
    }
}
