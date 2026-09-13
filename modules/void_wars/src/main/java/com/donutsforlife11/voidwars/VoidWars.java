package com.donutsforlife11.voidwars;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.ui.SidebarEntry;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWars extends GameModule {
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
                    player.chat(Component.text("Void Wars loaded as a blank UI test module.", NamedTextColor.GRAY));
                    player.gameMessage(Component.text("Sidebar, chat, and load countdown are active.", NamedTextColor.WHITE));
                }
            })
            .start();
    }

    @Override
    public void onStart() {
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
}
