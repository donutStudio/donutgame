package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWarsEvents {
    private final VoidWars game;

    private final List<BorderEvent> borderEvents = new ArrayList<>();
    private final List<ItemDropEvent> itemDropEvents = new ArrayList<>();
    private final List<ChestFillEvent> chestRefillEvents = new ArrayList<>();

    public VoidWarsEvents(VoidWars game) {
        this.game = game;
        game.mainTimer().onTick(20, timer -> {
            for (BorderEvent event : borderEvents) {
                if (event.seconds() * 20 == timer.getElapsedTicks()) {
                    shrinkBorder(event.multiplier());
                }
            }
            for (ItemDropEvent event : itemDropEvents) {
                if (event.seconds() * 20 == timer.getElapsedTicks()) {
                    giveItem(event.loot());
                }
            }
        });
    }

    public void shrinkBorder(double multiplier) {
        GameBorder border = game.mainBorder();
        Vector dimensions = border.dimensions();
        border.setDimensions(dimensions.multiply(multiplier), (int) Math.round(((multiplier - 1) * dimensions.length())/0.01));
        game.uiManager().subtitle(game.playerManager().getPlayers(), Component.text(
            "! ", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
            .append(Component.text("Border Shrinking", NamedTextColor.RED)).decoration(TextDecoration.BOLD, false)
            .append(Component.text(" !", NamedTextColor.DARK_RED)).decorate(TextDecoration.BOLD)
        );
    }
    public void giveItem(LootTable loot) {
        if (loot == null) {
            return;
        }
        Random random = new Random();
        for (Player player : game.playerManager().getPlayers()) {
            LootContext context = new LootContext.Builder(player.getLocation()).build();
            Collection<ItemStack> items = loot.populateLoot(random, context);
            if (items.isEmpty()) {
                continue;
            }
            // List<Component> itemNames = new ArrayList<>();
            player.give(items);
            /* for (ItemStack item : items) {
                itemNames.add(item.effectiveName());
            } */
        }
    }
    public void refillChests(LootTable loot) {
        if (loot == null) {
            return;
        }

    }
    public List<Chest> spawnChests(int count) {
        List<Location> locations = new ArrayList<>(game.world().getPoints("chest"));
        List<Chest> chests = new ArrayList<>();
        Collections.shuffle(locations);
        for (Location location : locations) {
            
        }
        return null; // stub
    }

    public record BorderEvent(int seconds, double multiplier) {
    }
    public record ItemDropEvent(int seconds, LootTable loot) {
    }
    public record ChestFillEvent(int seconds, LootTable loot) {
    }
}
