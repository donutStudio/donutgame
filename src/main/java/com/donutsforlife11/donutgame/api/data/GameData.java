package com.donutsforlife11.donutgame.api.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.item.GameItemService;

import net.kyori.adventure.text.Component;

public class GameData {
    private final GameModule module;
    private final GameItemService itemService;
    private final Map<String, YamlConfiguration> configurations = new ConcurrentHashMap<>();

    public GameData(GameModule module, GameItemService itemService) {
        this.module = module;
        this.itemService = itemService;
    }

    public YamlConfiguration configuration(String name) {
        return configurations.computeIfAbsent(name, key -> loadConfiguration(key + ".yml"));
    }

    public ItemStack item(String input) {
        return itemService.parseItem(input);
    }

    public ItemStack randomPool(List<String> pool) {
        return itemService.randomPool(pool);
    }

    public Collection<ItemStack> loot(String path) {
        return loot(path, null);
    }

    public Collection<ItemStack> loot(String path, GamePlayer player) {
        return itemService.loot(module, path, player);
    }

    public void give(GamePlayer player, Collection<ItemStack> items) {
        itemService.give(player, items);
    }

    public void give(GamePlayer player, ItemStack... items) {
        give(player, List.of(items));
    }

    public Component itemName(ItemStack item) {
        return itemService.displayName(item);
    }

    public Reader reader(String path) {
        InputStream stream = module.resource(path);
        if (stream == null) {
            throw new IllegalArgumentException("Missing module resource: " + path);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private YamlConfiguration loadConfiguration(String path) {
        try (Reader reader = reader(path)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load module configuration " + path, e);
        }
    }
}
