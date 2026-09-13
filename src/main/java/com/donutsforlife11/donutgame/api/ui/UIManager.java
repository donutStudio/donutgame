package com.donutsforlife11.donutgame.api.ui;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.ui.title.TitlePacketTracker;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;

public class UIManager {
    private final GameModule module;
    private final TitlePacketTracker titleTracker = new TitlePacketTracker();
    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();

    public UIManager(GameModule module) {
        this.module = module;
    }

    public void title(GamePlayer gamePlayer, Component title) {
        Player player = resolvePlayer(gamePlayer);
        if (player == null) {
            return;
        }
        player.sendTitlePart(TitlePart.TITLE, component(title));
        titleTracker.markTitle(player);
    }

    public void subtitle(GamePlayer gamePlayer, Component subtitle) {
        Player player = resolvePlayer(gamePlayer);
        if (player == null) {
            return;
        }
        if (!titleTracker.hasActiveTitle(player)) {
            player.sendTitlePart(TitlePart.TITLE, Component.empty());
            titleTracker.markTitle(player);
        }
        player.sendTitlePart(TitlePart.SUBTITLE, component(subtitle));
    }

    public void actionbar(GamePlayer gamePlayer, Component actionbar) {
        Player player = resolvePlayer(gamePlayer);
        if (player != null) {
            player.sendActionBar(component(actionbar));
        }
    }

    public void chat(GamePlayer gamePlayer, Component message) {
        Player player = resolvePlayer(gamePlayer);
        if (player != null) {
            player.sendMessage(component(message));
        }
    }

    public void gameMessage(GamePlayer gamePlayer, Component message) {
        Component body = component(message).decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.FALSE);
        chat(gamePlayer, Component.empty()
            .append(Component.text("Game > ", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(body));
    }

    public void playSound(GamePlayer gamePlayer, Sound sound, SoundCategory category, GameLocation location, float volume, float pitch, float minVolume) {
        Player player = resolvePlayer(gamePlayer);
        if (player == null || sound == null) {
            return;
        }
        SoundCategory resolvedCategory = category == null ? SoundCategory.MASTER : category;
        Location source = resolveLocation(player, location);
        if (!player.getWorld().equals(source.getWorld())) {
            if (minVolume > 0) {
                player.playSound(player.getLocation(), sound, resolvedCategory, minVolume, pitch);
            }
            return;
        }
        player.playSound(source, sound, resolvedCategory, volume, pitch);
    }

    public GameSidebar newSidebar() {
        GameSidebar sidebar = new GameSidebar(module);
        sidebars.add(sidebar);
        return sidebar;
    }

    public List<GameSidebar> sidebars() {
        return List.copyOf(sidebars);
    }

    public void clear(GamePlayer gamePlayer) {
        Player player = resolvePlayer(gamePlayer);
        if (player == null) {
            return;
        }
        player.sendTitlePart(TitlePart.TITLE, Component.empty());
        player.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
        player.sendActionBar(Component.empty());
        titleTracker.clear(player);
    }

    public void clear() {
        for (GameSidebar sidebar : List.copyOf(sidebars)) {
            sidebar.hide();
        }
        sidebars.clear();
        titleTracker.clear();
    }

    private Player resolvePlayer(GamePlayer gamePlayer) {
        return gamePlayer == null ? null : gamePlayer.bukkitPlayer();
    }

    private Location resolveLocation(Player player, GameLocation location) {
        if (location == null) {
            return player.getLocation();
        }
        GameWorld locationWorld = location.world();
        if (locationWorld != null) {
            return location.bukkitLocation();
        }
        GameWorld moduleWorld = module.world();
        World world = moduleWorld == null ? null : moduleWorld.bukkitWorld();
        return location.toBukkit(world == null ? player.getWorld() : world);
    }

    private Component component(Component component) {
        return component == null ? Component.empty() : component;
    }
}
