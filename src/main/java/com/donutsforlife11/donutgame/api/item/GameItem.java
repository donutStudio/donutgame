package com.donutsforlife11.donutgame.api.item;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import io.papermc.paper.datacomponent.DataComponentBuilder;
import io.papermc.paper.datacomponent.DataComponentType;

public final class GameItem {
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

    public static GameItem of(GameItemType type) {
        Objects.requireNonNull(type, "type");
        return of(type.material());
    }

    public static GameItem from(ItemStack item) {
        return item == null ? null : new GameItem(item);
    }

    public Material material() {
        return item.getType();
    }

    public GameItemType type() {
        return GameItemType.of(material());
    }

    public void setType(Material type) {
        item = item.withType(Objects.requireNonNull(type, "type"));
    }

    public int amount() {
        return item.getAmount();
    }

    public void setAmount(int amount) {
        item.setAmount(Math.max(0, amount));
    }

    public boolean isSimilar(GameItem other) {
        return other != null && item.isSimilar(other.item);
    }

    public void addEnchantment(Enchantment enchantment, int level) {
        item.addEnchantment(enchantment, level);
    }

    public void addUnsafeEnchantment(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
    }

    public ItemMeta getItemMeta() {
        return item.getItemMeta();
    }

    public boolean setItemMeta(ItemMeta itemMeta) {
        return item.setItemMeta(itemMeta);
    }

    public boolean editMeta(Consumer<? super ItemMeta> editor) {
        return item.editMeta(editor);
    }

    public <M extends ItemMeta> boolean editMeta(Class<M> metaType, Consumer<? super M> editor) {
        return item.editMeta(metaType, editor);
    }

    public Component displayName() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasItemName()) {
            return meta.itemName();
        }
        return Bukkit.getItemFactory().displayName(item);
    }

    public <T> T getData(DataComponentType.Valued<T> type) {
        return item.getData(type);
    }

    public <T> void setData(DataComponentType.Valued<T> type, T value) {
        item.setData(type, value);
    }

    public <T> void setData(DataComponentType.Valued<T> type, DataComponentBuilder<T> valueBuilder) {
        item.setData(type, valueBuilder);
    }

    public void setData(DataComponentType.NonValued type) {
        item.setData(type);
    }

    public void unsetData(DataComponentType type) {
        item.unsetData(type);
    }

    public <T> T getData(GameItemComponent<T> component) {
        Objects.requireNonNull(component, "component");
        return item.getPersistentDataContainer().get(component.key(), component.type());
    }

    public <T> void setData(GameItemComponent<T> component, T value) {
        Objects.requireNonNull(component, "component");
        editPersistentData(component, pdcType -> item.editPersistentDataContainer(pdc -> pdc.set(component.key(), pdcType, value)));
    }

    public void removeData(GameItemComponent<?> component) {
        Objects.requireNonNull(component, "component");
        item.editPersistentDataContainer(pdc -> pdc.remove(component.key()));
    }

    public GameItem configure(Consumer<? super ItemStack> configuration) {
        Objects.requireNonNull(configuration, "configuration");
        configuration.accept(item);
        return this;
    }

    ItemStack bukkitItem() {
        return item;
    }

    public ItemStack copyBukkitItem() {
        return item.clone();
    }

    @SuppressWarnings("unchecked")
    private <P, T> void editPersistentData(GameItemComponent<T> component, Consumer<PersistentDataType<P, T>> editor) {
        editor.accept((PersistentDataType<P, T>) component.type());
    }
}
