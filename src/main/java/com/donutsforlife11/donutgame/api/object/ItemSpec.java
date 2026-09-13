package com.donutsforlife11.donutgame.api.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemSpec {
    private final Material material;
    private final List<Consumer<? super ItemStack>> configurations;

    private ItemSpec(Material material, List<Consumer<? super ItemStack>> configurations) {
        this.material = Objects.requireNonNull(material, "material");
        this.configurations = List.copyOf(configurations);
    }

    public static ItemSpec of(Material material) {
        return new ItemSpec(material, List.of());
    }

    public Material material() {
        return material;
    }

    public ItemSpec configure(Consumer<? super ItemStack> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<Consumer<? super ItemStack>> nextConfigurations = new ArrayList<>(configurations);
        nextConfigurations.add(configuration);
        return new ItemSpec(material, nextConfigurations);
    }

    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(material);
        configurations.forEach(configuration -> configuration.accept(stack));
        return stack;
    }
}
