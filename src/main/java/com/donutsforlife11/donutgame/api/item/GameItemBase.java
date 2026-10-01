package com.donutsforlife11.donutgame.api.item;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.datacomponent.DataComponentBuilder;
import io.papermc.paper.datacomponent.DataComponentType;

public interface GameItemBase {
    ItemStack bukkitItem();

    default Material getType() {
        return bukkitItem().getType();
    }

    void setType(Material type);

    default int getAmount() {
        return bukkitItem().getAmount();
    }

    default void setAmount(int amount) {
        bukkitItem().setAmount(Math.max(0, amount));
    }

    default int getMaxStackSize() {
        return bukkitItem().getMaxStackSize();
    }

    default boolean isSimilar(GameItem item) {
        return item != null && bukkitItem().isSimilar(item.bukkitItem());
    }

    default boolean containsEnchantment(Enchantment enchantment) {
        return bukkitItem().containsEnchantment(enchantment);
    }

    default int getEnchantmentLevel(Enchantment enchantment) {
        return bukkitItem().getEnchantmentLevel(enchantment);
    }

    default Map<Enchantment, Integer> getEnchantments() {
        return bukkitItem().getEnchantments();
    }

    default void addEnchantment(Enchantment enchantment, int level) {
        bukkitItem().addEnchantment(enchantment, level);
    }

    default void addUnsafeEnchantment(Enchantment enchantment, int level) {
        bukkitItem().addUnsafeEnchantment(enchantment, level);
    }

    default int removeEnchantment(Enchantment enchantment) {
        return bukkitItem().removeEnchantment(enchantment);
    }

    default ItemMeta getItemMeta() {
        return bukkitItem().getItemMeta();
    }

    default boolean setItemMeta(ItemMeta itemMeta) {
        return bukkitItem().setItemMeta(itemMeta);
    }

    default boolean editMeta(Consumer<? super ItemMeta> editor) {
        return bukkitItem().editMeta(editor);
    }

    default void addItemFlags(ItemFlag... itemFlags) {
        bukkitItem().addItemFlags(itemFlags);
    }

    default void removeItemFlags(ItemFlag... itemFlags) {
        bukkitItem().removeItemFlags(itemFlags);
    }

    default Set<ItemFlag> getItemFlags() {
        return bukkitItem().getItemFlags();
    }

    default boolean hasItemFlag(ItemFlag itemFlag) {
        return bukkitItem().hasItemFlag(itemFlag);
    }

    default <T> T getData(DataComponentType.Valued<T> type) {
        return bukkitItem().getData(type);
    }

    default <T> T getDataOrDefault(DataComponentType.Valued<? extends T> type, T fallback) {
        return bukkitItem().getDataOrDefault(type, fallback);
    }

    default boolean hasData(DataComponentType type) {
        return bukkitItem().hasData(type);
    }

    default Set<DataComponentType> getDataTypes() {
        return bukkitItem().getDataTypes();
    }

    default <T> void setData(DataComponentType.Valued<T> type, T value) {
        bukkitItem().setData(type, value);
    }

    default <T> void setData(DataComponentType.Valued<T> type, DataComponentBuilder<T> valueBuilder) {
        bukkitItem().setData(type, valueBuilder);
    }

    default void setData(DataComponentType.NonValued type) {
        bukkitItem().setData(type);
    }

    default void unsetData(DataComponentType type) {
        bukkitItem().unsetData(type);
    }

    default void resetData(DataComponentType type) {
        bukkitItem().resetData(type);
    }

    default void copyDataFrom(GameItem source, Predicate<DataComponentType> filter) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }
        bukkitItem().copyDataFrom(source.bukkitItem(), filter);
    }

    default boolean isDataOverridden(DataComponentType type) {
        return bukkitItem().isDataOverridden(type);
    }

    default boolean matchesWithoutData(GameItem item, Set<DataComponentType> excludeTypes) {
        return item != null && bukkitItem().matchesWithoutData(item.bukkitItem(), excludeTypes);
    }

    default boolean matchesWithoutData(GameItem item, Set<DataComponentType> excludeTypes, boolean ignoreCount) {
        return item != null && bukkitItem().matchesWithoutData(item.bukkitItem(), excludeTypes, ignoreCount);
    }

    default <T> T getData(GameItemComponent<T> component) {
        Objects.requireNonNull(component, "component");
        return bukkitItem().getPersistentDataContainer().get(component.key(), component.type());
    }

    default <T> T getDataOrDefault(GameItemComponent<T> component, T fallback) {
        Objects.requireNonNull(component, "component");
        return bukkitItem().getPersistentDataContainer().getOrDefault(component.key(), component.type(), fallback);
    }

    default <T> boolean hasData(GameItemComponent<T> component) {
        Objects.requireNonNull(component, "component");
        return bukkitItem().getPersistentDataContainer().has(component.key(), component.type());
    }

    default <T> void setData(GameItemComponent<T> component, T value) {
        Objects.requireNonNull(component, "component");
        editPersistentData(component, pdcType -> bukkitItem().editPersistentDataContainer(pdc -> pdc.set(component.key(), pdcType, value)));
    }

    default void removeData(GameItemComponent<?> component) {
        Objects.requireNonNull(component, "component");
        bukkitItem().editPersistentDataContainer(pdc -> pdc.remove(component.key()));
    }

    @SuppressWarnings("unchecked")
    private <P, T> void editPersistentData(GameItemComponent<T> component, Consumer<PersistentDataType<P, T>> editor) {
        editor.accept((PersistentDataType<P, T>) component.type());
    }
}
