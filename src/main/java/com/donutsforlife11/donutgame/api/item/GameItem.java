package com.donutsforlife11.donutgame.api.item;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class GameItem implements GameItemBase {
    private ItemStack item;

    public GameItem(Material material) {
        this(new ItemStack(Objects.requireNonNull(material, "material")));
    }

    public GameItem(ItemStack item) {
        this.item = GameItemComponents.normalize(Objects.requireNonNull(item, "item").clone());
    }

    public static GameItem of(Material material) {
        return new GameItem(material);
    }

    public static GameItem from(ItemStack item) {
        return item == null ? null : new GameItem(item);
    }

    public Material material() {
        return getType();
    }

    @Override
    public void setType(Material type) {
        item = item.withType(Objects.requireNonNull(type, "type"));
    }

    public int amount() {
        return getAmount();
    }

    public GameItem configure(Consumer<? super ItemStack> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        configuration.accept(item);
        return this;
    }

    @Override
    public ItemStack bukkitItem() {
        return item;
    }

    public ItemStack copyBukkitItem() {
        return item.clone();
    }
}
