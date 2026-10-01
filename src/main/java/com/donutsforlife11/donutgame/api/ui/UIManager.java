package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
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
        title(single(gamePlayer), title);
    }

    public void title(Collection<GamePlayer> gamePlayers, Component title) {
        Component component = component(title);
        for (Player player : resolvePlayers(gamePlayers)) {
            player.sendTitlePart(TitlePart.TITLE, component);
            titleTracker.markTitle(player);
        }
    }

    public void subtitle(GamePlayer gamePlayer, Component subtitle) {
        subtitle(single(gamePlayer), subtitle);
    }

    public void subtitle(Collection<GamePlayer> gamePlayers, Component subtitle) {
        Component component = component(subtitle);
        for (Player player : resolvePlayers(gamePlayers)) {
            if (!titleTracker.hasActiveTitle(player)) {
                player.sendTitlePart(TitlePart.TITLE, Component.empty());
                titleTracker.markTitle(player);
            }
            player.sendTitlePart(TitlePart.SUBTITLE, component);
        }
    }

    public void actionbar(GamePlayer gamePlayer, Component actionbar) {
        actionbar(single(gamePlayer), actionbar);
    }

    public void actionbar(Collection<GamePlayer> gamePlayers, Component actionbar) {
        Component component = component(actionbar);
        for (Player player : resolvePlayers(gamePlayers)) {
            player.sendActionBar(component);
        }
    }

    public void chat(GamePlayer gamePlayer, Component message) {
        chat(single(gamePlayer), message);
    }

    public void chat(Collection<GamePlayer> gamePlayers, Component message) {
        Component component = component(message);
        for (Player player : resolvePlayers(gamePlayers)) {
            player.sendMessage(component);
        }
    }

    public void gameMessage(GamePlayer gamePlayer, Component message) {
        gameMessage(single(gamePlayer), message);
    }

    public void gameMessage(Collection<GamePlayer> gamePlayers, Component message) {
        Component body = component(message).decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.FALSE);
        chat(gamePlayers, Component.empty()
            .append(Component.text("Game > ", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(body));
    }

    public void playSound(GamePlayer gamePlayer, GameSound sound) {
        playSound(single(gamePlayer), sound);
    }

    public void playSound(Collection<GamePlayer> gamePlayers, GameSound sound) {
        if (sound == null || sound.sound() == null) {
            return;
        }
        for (Player player : resolvePlayers(gamePlayers)) {
            Location source = resolveLocation(player, sound.location());
            if (!player.getWorld().equals(source.getWorld())) {
                if (sound.minVolume() > 0) {
                    player.playSound(player.getLocation(), sound.sound(), sound.category(), sound.minVolume(), sound.pitch());
                }
                continue;
            }
            player.playSound(source, sound.sound(), sound.category(), sound.volume(), sound.pitch());
        }
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

    private Collection<GamePlayer> single(GamePlayer gamePlayer) {
        return gamePlayer == null ? List.of() : List.of(gamePlayer);
    }

    private Collection<Player> resolvePlayers(Collection<GamePlayer> gamePlayers) {
        List<Player> players = new ArrayList<>();
        if (gamePlayers == null) {
            return players;
        }
        for (GamePlayer gamePlayer : gamePlayers) {
            Player player = resolvePlayer(gamePlayer);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
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
