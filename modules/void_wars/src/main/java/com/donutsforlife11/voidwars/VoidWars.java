package com.donutsforlife11.voidwars;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.item.ItemSpec;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.ui.SidebarEntry;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.datacomponent.DataComponentTypes;

public class VoidWars extends GameModule {
    private static final List<ItemSpec> COMPONENT_TEST_ITEMS = List.of(
        testItem(Material.WHITE_WOOL, "Infinite Build Wool x7", NamedTextColor.GREEN, item -> {
            item.setAmount(7);
            item.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
        }),
        testItem(Material.WHITE_WOOL, "Infinite Build Wool x7 Max 16", NamedTextColor.GREEN, item -> {
            item.setAmount(7);
            item.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        }),
        testItem(Material.WHITE_WOOL, "Team Sync Wool", NamedTextColor.AQUA, item -> item.setData(GameItemComponents.TEAM_SYNC, (byte) 1)),
        testItem(Material.LEATHER_CHESTPLATE, "Team Sync Leather Chestplate", NamedTextColor.AQUA, item -> item.setData(GameItemComponents.TEAM_SYNC, (byte) 1)),
        testItem(Material.WOLF_ARMOR, "Team Sync Wolf Armor", NamedTextColor.AQUA, item -> item.setData(GameItemComponents.TEAM_SYNC, (byte) 1)),
        testItem(Material.WHITE_WOOL, "Team Infinite Wool x7", NamedTextColor.YELLOW, item -> {
            item.setAmount(7);
            item.setData(GameItemComponents.TEAM_SYNC, (byte) 1);
            item.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
        }),
        testItem(Material.TNT, null, NamedTextColor.RED, item -> {
            item.setAmount(4);
            item.setData(GameItemComponents.AUTO_IGNITE, 80);
        }),
        testItem(Material.TNT, "Explicit Named Auto TNT", NamedTextColor.RED, item -> {
            item.setAmount(4);
            item.setData(GameItemComponents.AUTO_IGNITE, 80);
        }),
        testItem(Material.CREEPER_SPAWN_EGG, "Auto Ignite Creeper Egg", NamedTextColor.RED, item -> item.setData(GameItemComponents.AUTO_IGNITE, 80)),
        testItem(Material.COW_SPAWN_EGG, "Silent Cow Egg", NamedTextColor.GRAY, item -> item.setData(GameItemComponents.AUTO_IGNITE, 80)),
        testItem(Material.BLUE_CONCRETE, null, NamedTextColor.GOLD, item -> {
            item.setAmount(8);
            item.setData(GameItemComponents.AUTO_IGNITE, 60);
        }),
        testItem(Material.RED_WOOL, "Infinite Auto Wool x5", NamedTextColor.GOLD, item -> {
            item.setAmount(5);
            item.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
            item.setData(GameItemComponents.AUTO_IGNITE, 60);
        }),
        testItem(Material.WHITE_CONCRETE, "Team Infinite Auto Concrete x5", NamedTextColor.LIGHT_PURPLE, item -> {
            item.setAmount(5);
            item.setData(GameItemComponents.TEAM_SYNC, (byte) 1);
            item.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
            item.setData(GameItemComponents.AUTO_IGNITE, 60);
        })
    );

    private int sidebarTicks;

    @Override
    public void onLoad() {
        GameLocation spawn = world().getPoint("spawn");
        if (spawn != null) {
            world().setWorldSpawn(spawn);
        }

        uiManager().newSidebar()
            .addEntry(SidebarEntry.custom(Component.text("Sidebar API Test", NamedTextColor.GRAY)))
            .addEntry(SidebarEntry.blank())
            .addEntry(SidebarEntry.integer("Players", () -> playerManager().getOnlinePlayers().size()))
            .addEntry(SidebarEntry.integer("Your State", player -> player.isOnline() ? 1 : 0))
            .addEntry(SidebarEntry.fraction("Map Slots", () -> playerManager().getOnlinePlayers().size(), () -> Math.max(1, playerManager().getPlayers().size())))
            .addEntry(SidebarEntry.time("Runtime", () -> sidebarTicks))
            .addEntry(SidebarEntry.component("Viewer", player -> Component.text(player.uuid().toString().substring(0, 8), NamedTextColor.AQUA)))
            .setRefreshInterval(10)
            .show();

        timeManager().newTimer()
            .onTick(timer -> sidebarTicks = timer.elapsedTicks())
            .start();

        timeManager().newTimer(1)
            .onFinish(ignored -> {
                for (GamePlayer player : playerManager().getOnlinePlayers()) {
                    player.setSpectatablePlayers(() -> playerManager().getPlayers());
                    player.chat(Component.text("Void Wars loaded as a blank UI test module.", NamedTextColor.GRAY));
                    player.gameMessage(Component.text("Sidebar, chat, and load countdown are active.", NamedTextColor.WHITE));
                }
            })
            .start();
    }

    @Override
    public void onStart() {
        for (GamePlayer player : playerManager().getOnlinePlayers()) {
            player.setSpectatablePlayers(() -> playerManager().getPlayers());
            for (ItemSpec item : COMPONENT_TEST_ITEMS) {
                player.giveItem(item);
            }
        }

        timeManager().newTimer(80)
            .onFinish(ignored -> {
                for (GamePlayer player : playerManager().getOnlinePlayers()) {
                    player.title(Component.text("Void Wars", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                }

                timeManager().newTimer(100)
                    .onFinish(ignored2 -> {
                        for (GamePlayer player : playerManager().getOnlinePlayers()) {
                            player.subtitle(Component.text("Subtitle without a fresh title", NamedTextColor.YELLOW));
                        }

                        timeManager().newTimer(100)
                            .onFinish(ignored3 -> {
                                for (GamePlayer player : playerManager().getOnlinePlayers()) {
                                    player.title(Component.text("Ready", NamedTextColor.GREEN, TextDecoration.BOLD));
                                    player.subtitle(Component.text("Title, subtitle, actionbar, and sound together", NamedTextColor.WHITE));
                                    player.actionbar(Component.text("Actionbar test", NamedTextColor.AQUA));
                                    player.playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.4f);
                                }
                            })
                            .start();
                    })
                    .start();
            })
            .start();
    }

    @GameEventHandler
    public void onDeath(GameEvent<PlayerDeathEvent> event) {
        GamePlayer player = event.getPlayer();
        player.respawn(200);
    }

    private static ItemSpec testItem(Material material, String name, NamedTextColor color, java.util.function.Consumer<com.donutsforlife11.donutgame.api.item.GameItem> configure) {
        return ItemSpec.of(material).configure(item -> {
            if (name != null) {
                item.setData(DataComponentTypes.ITEM_NAME, Component.text(name, color));
            }
            configure.accept(item);
        });
    }
}
