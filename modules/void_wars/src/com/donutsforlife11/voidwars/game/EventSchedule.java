package com.donutsforlife11.voidwars.game;

import java.util.List;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.voidwars.VoidWars;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class EventSchedule {
    private static TimeManager timeManager;
    private static UIManager uiManager;
    private static PlayerManager playerManager;
    private static GameWorld world;

    public static void init(VoidWars voidWars) {
        timeManager = voidWars.timeManager();
        uiManager = voidWars.uiManager();
        playerManager = voidWars.playerManager();
        world = voidWars.world();
    }

    public static GameTimer groundCollapseTimer(int seconds) {
        return timeManager.formattedCountdown(seconds * 20, formattedNumber -> {
            uiManager.actionbar(Audience.audience(playerManager.getPlayers()), Component.text("Ground collapses in ").append(formattedNumber));
        }).onEnd(timer -> {
            for (BoundingBox box : world.getRegions("spawn_platform")) {
                world.fillArea(box, Material.AIR);
            }
        });
    }

    public static GameTimer pvpEnablementTimer(int seconds) {
        return timeManager.createTimer(seconds * 20).onEnd(timer -> {
            world.getBukkitWorld().setGameRule(GameRules.PVP, true);
            uiManager.gameMessage(Audience.audience(playerManager.getPlayers()), Component.text("PvP is now enabled!"));
        });
    }

    public static void spawnChests(List<Location> locations) {
        for (Location location : locations) {
            world.spawnLootChest(location, null);
            uiManager.createGlow().setTarget(location.getBlock()).setAudience(Audience.audience(playerManager.getPlayers())).setColor(NamedTextColor.RED);
        }
    }
}
