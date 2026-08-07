package com.donutsforlife11.voidwars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;

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
    }

    public void bind(GameTimer timer) {
        timer.onTick(20, ignored -> {
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
        if (border == null) {
            return;
        }
        Vector dimensions = border.dimensions();
        border.setDimensions(dimensions.multiply(multiplier), (int) Math.round(Math.abs((multiplier - 1) * dimensions.length()) / 0.01));
        game.uiManager().subtitlePlayers(game.playerManager().getPlayers(), Component.text(
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
        for (GamePlayer player : game.playerManager().getPlayers()) {
            if (player.player() == null) {
                continue;
            }
            LootContext context = new LootContext.Builder(player.player().getLocation()).build();
            Collection<ItemStack> items = loot.populateLoot(random, context);
            if (items.isEmpty()) {
                continue;
            }
            player.player().give(items);
        }
    }

    public void refillChests(LootTable loot) {
        if (loot == null) {
            return;
        }
    }

    public List<Chest> spawnChests(int count) {
        List<com.donutsforlife11.donutgame.api.map.GameLocation> locations = new ArrayList<>(game.world().getPoints("chest"));
        List<Chest> chests = new ArrayList<>();
        Collections.shuffle(locations);
        return chests;
    }

    public record BorderEvent(int seconds, double multiplier) {
    }

    public record ItemDropEvent(int seconds, LootTable loot) {
    }

    public record ChestFillEvent(int seconds, LootTable loot) {
    }
}
