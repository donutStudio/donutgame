package com.donutsforlife11.voidwars.game;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.voidwars.VoidWars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Ui {
    private static VoidWars game;
    private static UIManager uiManager;

    public static void init(VoidWars voidWars) {
        uiManager = voidWars.uiManager();
    }

    public static void eliminationScreen(Player player) {
        if (game.config().getInt("team_size") == 1) {
            uiManager.title(player, Component.text("Eliminated", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }
    }
}
