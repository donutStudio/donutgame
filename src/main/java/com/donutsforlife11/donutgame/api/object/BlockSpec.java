package com.donutsforlife11.donutgame.api.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

public final class BlockSpec<D extends BlockData> {
    private final Material material;
    private final Class<D> dataType;
    private final List<Consumer<? super D>> dataConfigurations;
    private final List<StateConfiguration<?>> stateConfigurations;

    private BlockSpec(
        Material material,
        Class<D> dataType,
        List<Consumer<? super D>> dataConfigurations,
        List<StateConfiguration<?>> stateConfigurations
    ) {
        this.material = Objects.requireNonNull(material, "material");
        this.dataType = Objects.requireNonNull(dataType, "dataType");
        this.dataConfigurations = List.copyOf(dataConfigurations);
        this.stateConfigurations = List.copyOf(stateConfigurations);
    }

    public static BlockSpec<BlockData> of(Material material) {
        return of(material, BlockData.class);
    }

    public static <D extends BlockData> BlockSpec<D> of(Material material, Class<D> dataType) {
        return new BlockSpec<>(material, dataType, List.of(), List.of());
    }

    public Material material() {
        return material;
    }

    public Class<D> dataType() {
        return dataType;
    }

    public BlockSpec<D> configure(Consumer<? super D> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<Consumer<? super D>> configurations = new ArrayList<>(dataConfigurations);
        configurations.add(configuration);
        return new BlockSpec<>(material, dataType, configurations, stateConfigurations);
    }

    public <S extends BlockState> BlockSpec<D> configureState(Class<S> stateType, Consumer<? super S> configuration) {
        Objects.requireNonNull(stateType, "stateType");
        Objects.requireNonNull(configuration, "configuration");
        List<StateConfiguration<?>> configurations = new ArrayList<>(stateConfigurations);
        configurations.add(new StateConfiguration<>(stateType, configuration));
        return new BlockSpec<>(material, dataType, dataConfigurations, configurations);
    }

    public D createBlockData() {
        D data = dataType.cast(Bukkit.createBlockData(material));
        dataConfigurations.forEach(configuration -> configuration.accept(data));
        return data;
    }

    public void applyTo(Block block) {
        Objects.requireNonNull(block, "block");
        block.setBlockData(createBlockData(), false);
        if (stateConfigurations.isEmpty()) {
            return;
        }
        BlockState state = block.getState();
        stateConfigurations.forEach(configuration -> configuration.apply(state));
        state.update(true, false);
    }

    public boolean matches(Block block) {
        Objects.requireNonNull(block, "block");
        if (block.getType() != material) {
            return false;
        }
        return dataConfigurations.isEmpty() && stateConfigurations.isEmpty()
            || block.getBlockData().equals(createBlockData());
    }

    private record StateConfiguration<S extends BlockState>(
        Class<S> stateType,
        Consumer<? super S> configuration
    ) {
        private void apply(BlockState state) {
            if (!stateType.isInstance(state)) {
                throw new IllegalArgumentException("Block state " + state.getClass().getSimpleName()
                    + " is not a " + stateType.getSimpleName() + ".");
            }
            configuration.accept(stateType.cast(state));
        }
    }
}
