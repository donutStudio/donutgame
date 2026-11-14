package com.donutsforlife11.donutgame.api.map;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import com.donutsforlife11.donutgame.api.item.GameLootTable;
import com.donutsforlife11.donutgame.api.item.GameLootTableHandle;

import com.donutsforlife11.donutgame.api.object.GameBlockType;

public final class GameBlock extends PaperBlockMethods {
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

    public GameLocation getLocation() {
        world.requireCurrentGeneration(generation, "GameBlock");
        return new GameLocation(world, x, y, z);
    }

    public boolean is(Material material) {
        return material != null && getType() == material;
    }

    public boolean is(GameBlockType type) {
        return type != null && is(type.material());
    }

    public void setType(GameBlockType type) {
        setType(Objects.requireNonNull(type, "type").material());
    }

    private <S extends BlockState> void editState(Class<S> stateType, Consumer<? super S> editor) {
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
        editState(Container.class, container -> container.getSnapshotInventory().clear());
    }

    public void setLootTable(GameLootTable lootTable) {
        setLootTable(lootTable, 0L);
    }

    public void setLootTable(GameLootTable lootTable, long seed) {
        LootTable resolved = GameLootTableHandle.resolve(lootTable, world);
        editState(BlockState.class, state -> {
            if (!(state instanceof Lootable lootable)) {
                throw new IllegalStateException("Block at " + x + ", " + y + ", " + z + " cannot use a loot table.");
            }
            if (state instanceof Container container) {
                container.getSnapshotInventory().clear();
            }
            lootable.setLootTable(resolved);
            lootable.setSeed(seed);
        });
    }

    Block block() {
        return world.requireBukkitWorld(generation).getBlockAt(x, y, z);
    }
}
