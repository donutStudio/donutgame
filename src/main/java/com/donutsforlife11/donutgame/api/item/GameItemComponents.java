package com.donutsforlife11.donutgame.api.item;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GameItemComponents {
    public static final GameItemComponent<Byte> INFINITE_BUILD = bool("infinite_build");
    public static final GameItemComponent<Byte> TEAM_SYNC = bool("team_sync");
    public static final GameItemComponent<Byte> VANILLA_PROJECTILE = bool("vanilla_projectile");
    public static final GameItemComponent<Integer> AUTO_IGNITE = integer("auto_ignite");
    public static final GameItemComponent<String> SPECTATOR_MENU = string("spectator_menu");

    private static final String NAMESPACE = "donutgame";
    private static final DyeColor[] DYE_COLORS = DyeColor.values();

    private GameItemComponents() {
    }

    public static GameItemComponent<Byte> bool(String key) {
        return of(key, PersistentDataType.BYTE);
    }

    public static GameItemComponent<Integer> integer(String key) {
        return of(key, PersistentDataType.INTEGER);
    }

    public static GameItemComponent<String> string(String key) {
        return of(key, PersistentDataType.STRING);
    }

    public static <T> GameItemComponent<T> of(String key, PersistentDataType<?, T> type) {
        return new GameItemComponent<>(new NamespacedKey(NAMESPACE, key), type);
    }

    public static <T> GameItemComponent<T> of(NamespacedKey key, PersistentDataType<?, T> type) {
        return new GameItemComponent<>(key, type);
    }

    public static boolean hasInfiniteBuild(ItemStack item) {
        return booleanValue(item, INFINITE_BUILD);
    }

    public static boolean hasTeamSync(ItemStack item) {
        return booleanValue(item, TEAM_SYNC);
    }

    public static boolean hasVanillaProjectile(ItemStack item) {
        return booleanValue(item, VANILLA_PROJECTILE);
    }

    public static int autoIgniteFuse(ItemStack item) {
        if (item == null) {
            return 0;
        }
        Integer value = item.getPersistentDataContainer().get(AUTO_IGNITE.key(), AUTO_IGNITE.type());
        return value == null ? 0 : Math.max(0, value);
    }

    public static boolean isSpectatorMenu(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getPersistentDataContainer().has(SPECTATOR_MENU.key(), SPECTATOR_MENU.type());
    }

    public static ItemStack normalize(ItemStack item) {
        return normalize(null, item);
    }

    public static ItemStack normalize(GamePlayer player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (player != null && hasTeamSync(item)) {
            item = applyTeamSync(player.getTeam(), item);
        }
        if (hasInfiniteBuild(item) && !item.isDataOverridden(DataComponentTypes.MAX_STACK_SIZE)) {
            item.unsetData(DataComponentTypes.DAMAGE);
            item.unsetData(DataComponentTypes.MAX_DAMAGE);
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 65);
        }
        if (autoIgniteFuse(item) > 0 && !hasExplicitItemName(item)) {
            Component name = item.getData(DataComponentTypes.ITEM_NAME);
            item.setData(DataComponentTypes.ITEM_NAME, Component.text("Auto Ignite ").append(name == null ? Component.empty() : name));
        }
        return item;
    }

    public static DyeColor teamDyeColor(GamePlayer player) {
        return teamDyeColor(player == null ? null : player.getTeam());
    }

    public static DyeColor teamDyeColor(GameTeam team) {
        if (team == null) {
            return DyeColor.WHITE;
        }
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

    private static ItemStack applyTeamSync(GameTeam team, ItemStack item) {
        DyeColor dyeColor = teamDyeColor(team);
        Material dyedType = dyedVariant(dyeColor, item.getType());
        if (dyedType != null) {
            item = item.withType(dyedType);
        } else if (item.hasData(DataComponentTypes.DYED_COLOR) || item.getType().name().startsWith("LEATHER_") || item.getType() == Material.WOLF_ARMOR) {
            item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(teamColor(team)));
        }
        item.editPersistentDataContainer(pdc -> pdc.remove(TEAM_SYNC.key()));
        return item;
    }

    private static Color teamColor(GameTeam team) {
        return team == null ? Color.WHITE : Color.fromRGB(team.color().value());
    }

    private static Material dyedVariant(DyeColor dyeColor, Material material) {
        String materialName = material.name();
        for (DyeColor current : DYE_COLORS) {
            String prefix = current.name() + "_";
            if (materialName.startsWith(prefix)) {
                return Material.matchMaterial(dyeColor.name() + "_" + materialName.substring(prefix.length()));
            }
        }
        return null;
    }

    private static boolean booleanValue(ItemStack item, GameItemComponent<Byte> component) {
        if (item == null) {
            return false;
        }
        Byte value = item.getPersistentDataContainer().get(component.key(), component.type());
        if (value != null) {
            return value != 0;
        }
        Boolean legacyBoolean = item.getPersistentDataContainer().get(component.key(), PersistentDataType.BOOLEAN);
        if (legacyBoolean != null) {
            return legacyBoolean;
        }
        Integer legacyInteger = item.getPersistentDataContainer().get(component.key(), PersistentDataType.INTEGER);
        return legacyInteger != null && legacyInteger != 0;
    }

    private static boolean hasExplicitItemName(ItemStack item) {
        return item.isDataOverridden(DataComponentTypes.ITEM_NAME) || item.isDataOverridden(DataComponentTypes.CUSTOM_NAME);
    }
}
