package com.donutsforlife11.microrush;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public final class GameKits {
    private GameKits() { }

    public static void give(GameModule game, GamePlayer player, ConfigurationSection kit) {
        if (kit == null) return;
        // TODO: Review text and string identifiers below before release.
        slots(game, player, kit.getConfigurationSection("hotbar"), 0, 9);
        // TODO: Review text and string identifiers below before release.
        slots(game, player, kit.getConfigurationSection("inventory"), 9, 27);
        // TODO: Review text and string identifiers below before release.
        var armor = Map.of("feet", 36, "legs", 37, "chest", 38, "head", 39);
        armor.forEach((name, slot) -> {
            // TODO: Review text and string identifiers below before release.
            String value = kit.getString("armor." + name);
            if (value != null) player.setInventoryItem(slot, game.data().item(value));
        });
        // TODO: Review text and string identifiers below before release.
        String offhand = kit.getString("weapon.offhand");
        if (offhand != null) player.setInventoryItem(40, game.data().item(offhand));
        // TODO: Review text and string identifiers below before release.
        for (String value : kit.getStringList("items")) player.giveItem(game.data().item(value));
    }

    private static void slots(GameModule game, GamePlayer player, ConfigurationSection section, int offset, int length) {
        if (section == null) return;
        for (String key : section.getKeys(false).stream().sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList()) {
            int slot = Integer.parseInt(key);
            // TODO: Review text and string identifiers below before release.
            if (slot < 0 || slot >= length) throw new IllegalArgumentException("Invalid kit slot " + section.getCurrentPath() + "." + key);
            player.setInventoryItem(offset + slot, game.data().item(section.getString(key)));
        }
    }
}
