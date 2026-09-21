package com.donutsforlife11.donutgame.api.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemSpec {
    private final Material material;
    private final List<Consumer<? super GameItem>> configurations;

    private ItemSpec(Material material, List<Consumer<? super GameItem>> configurations) {
        this.material = Objects.requireNonNull(material, "material");
        this.configurations = List.copyOf(configurations);
    }

    public static ItemSpec of(Material material) {
        return new ItemSpec(material, List.of());
    }

    public Material material() {
        return material;
    }

    public ItemSpec configure(Consumer<? super GameItem> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<Consumer<? super GameItem>> nextConfigurations = new ArrayList<>(configurations);
        nextConfigurations.add(configuration);
        return new ItemSpec(material, nextConfigurations);
    }

    public GameItem createItem() {
        GameItem item = GameItem.of(material);
        for (Consumer<? super GameItem> configuration : configurations) {
            configuration.accept(item);
        }
        return item;
    }

    public ItemStack createItemStack() {
        return createItem().copyBukkitItem();
    }

    public boolean matches(GameItem item) {
        return item != null && matches(item.bukkitItem());
    }

    public boolean matches(ItemStack stack) {
        return stack != null && stack.getType() == material
            && (configurations.isEmpty() || stack.isSimilar(createItem().bukkitItem()));
    }
}
