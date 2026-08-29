package com.donutsforlife11.donutgame.api.data;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GameItemAttributes {
    public static final NamespacedKey AUTO_IGNITE = new NamespacedKey("donutgame", "auto_ignite");
    public static final NamespacedKey INFINITE_BUILD = new NamespacedKey("donutgame", "infinite_build");
    public static final NamespacedKey TEAM_SYNC = new NamespacedKey("donutgame", "team_sync");
    private static final DyeColor[] DYE_COLORS = DyeColor.values();

    private GameItemAttributes() {
    }

    public static boolean hasInfiniteBuild(ItemStack item) {
        return booleanValue(item, INFINITE_BUILD);
    }

    public static boolean hasTeamSync(ItemStack item) {
        return booleanValue(item, TEAM_SYNC);
    }

    public static int autoIgniteFuse(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer value = item.getItemMeta().getPersistentDataContainer().get(AUTO_IGNITE, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public static ItemStack withInfiniteBuild(ItemStack item) {
        return withBoolean(item, INFINITE_BUILD, true);
    }

    public static ItemStack withTeamSync(ItemStack item) {
        return withBoolean(item, TEAM_SYNC, true);
    }

    public static ItemStack withAutoIgnite(ItemStack item, int fuseTicks) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        copy.editMeta(meta -> meta.getPersistentDataContainer().set(AUTO_IGNITE, PersistentDataType.INTEGER, Math.max(0, fuseTicks)));
        normalize(copy);
        return copy;
    }

    public static ItemStack normalize(ItemStack item) {
        if (item == null) return null;
        if (hasInfiniteBuild(item) && !item.isDataOverridden(DataComponentTypes.MAX_STACK_SIZE)) {
            item.unsetData(DataComponentTypes.DAMAGE);
            item.unsetData(DataComponentTypes.MAX_DAMAGE);
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 65);
        }
        int fuse = autoIgniteFuse(item);
        if (fuse > 0 && !hasExplicitName(item)) {
            item.setData(DataComponentTypes.ITEM_NAME, Component.text("Auto Ignite " + defaultPlainItemName(item)));
        }
        return item;
    }

    public static DyeColor teamDyeColor(GamePlayer player) {
        return teamDyeColor(player == null ? null : player.team());
    }

    public static DyeColor teamDyeColor(GameTeam team) {
        if (team == null) return DyeColor.WHITE;
        NamedTextColor color = team.color();
        if (color == NamedTextColor.BLACK) return DyeColor.BLACK;
        if (color == NamedTextColor.DARK_BLUE || color == NamedTextColor.BLUE) return DyeColor.BLUE;
        if (color == NamedTextColor.DARK_GREEN) return DyeColor.GREEN;
        if (color == NamedTextColor.GREEN) return DyeColor.LIME;
        if (color == NamedTextColor.DARK_AQUA || color == NamedTextColor.AQUA) return DyeColor.CYAN;
        if (color == NamedTextColor.DARK_RED || color == NamedTextColor.RED) return DyeColor.RED;
        if (color == NamedTextColor.DARK_PURPLE) return DyeColor.PURPLE;
        if (color == NamedTextColor.LIGHT_PURPLE) return DyeColor.MAGENTA;
        if (color == NamedTextColor.GOLD) return DyeColor.ORANGE;
        if (color == NamedTextColor.YELLOW) return DyeColor.YELLOW;
        if (color == NamedTextColor.DARK_GRAY) return DyeColor.GRAY;
        if (color == NamedTextColor.GRAY) return DyeColor.LIGHT_GRAY;
        return DyeColor.WHITE;
    }

    public static Material teamDyeMaterial(GameTeam team) {
        Material material = Material.matchMaterial(teamDyeColor(team).name() + "_DYE");
        return material == null ? Material.WHITE_DYE : material;
    }

    public static Color teamBukkitColor(GamePlayer player) {
        return teamBukkitColor(player == null ? null : player.team());
    }

    public static Color teamBukkitColor(GameTeam team) {
        if (team == null) return Color.WHITE;
        return Color.fromRGB(team.color().value());
    }

    public static ItemStack teamSyncedItem(GamePlayer player, ItemStack item) {
        return teamSyncedItem(player == null ? null : player.team(), item);
    }

    public static ItemStack teamSyncedItem(GameTeam team, ItemStack item) {
        if (!hasTeamSync(item)) return item;
        ItemStack colored = colorDirectly(team, item);
        if (colored != null) return consumeTeamSync(colored);
        Material material = dyedVariant(teamDyeColor(team), item.getType());
        if (material == null) return item;
        ItemStack synced = new ItemStack(material, item.getAmount());
        if (item.hasItemMeta()) synced.setItemMeta(item.getItemMeta().clone());
        return consumeTeamSync(synced);
    }

    private static ItemStack consumeTeamSync(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().remove(TEAM_SYNC));
        return normalize(item);
    }

    private static boolean hasExplicitName(ItemStack item) {
        if (item.isDataOverridden(DataComponentTypes.ITEM_NAME) || item.isDataOverridden(DataComponentTypes.CUSTOM_NAME)) {
            return true;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasItemName() || meta.hasDisplayName();
    }

    private static String defaultPlainItemName(ItemStack item) {
        String key = item.translationKey();
        int separator = key.lastIndexOf('.');
        String name = separator < 0 ? key : key.substring(separator + 1);
        StringBuilder formatted = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (!formatted.isEmpty()) formatted.append(' ');
            formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return formatted.isEmpty() ? item.getType().name() : formatted.toString();
    }

    private static ItemStack withBoolean(ItemStack item, NamespacedKey key, boolean value) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        copy.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, value));
        return copy;
    }

    private static boolean booleanValue(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        Boolean bool = data.get(key, PersistentDataType.BOOLEAN);
        if (bool != null) return bool;
        Byte byteValue = data.get(key, PersistentDataType.BYTE);
        if (byteValue != null) return byteValue != 0;
        Integer intValue = data.get(key, PersistentDataType.INTEGER);
        return intValue != null && intValue != 0;
    }

    private static ItemStack colorDirectly(GameTeam team, ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof ColorableArmorMeta armorMeta)) return null;
        ItemStack synced = item.clone();
        armorMeta.setColor(teamBukkitColor(team));
        synced.setItemMeta(armorMeta);
        return synced;
    }

    private static Material dyedVariant(DyeColor dyeColor, Material material) {
        String materialName = material.name();
        for (DyeColor current : DYE_COLORS) {
            String prefix = current.name() + "_";
            if (!materialName.startsWith(prefix)) continue;
            String suffix = materialName.substring(prefix.length());
            return Material.matchMaterial(dyeColor.name() + "_" + suffix);
        }
        return null;
    }
}
