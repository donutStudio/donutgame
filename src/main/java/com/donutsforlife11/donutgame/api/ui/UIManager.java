package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.api.ui.title.TitlePacketTracker;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;

public class UIManager {
    private final GameModule module;
    private final TitlePacketTracker titleTracker = new TitlePacketTracker();
    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();
    private final Set<GameBossbar> bossbars = ConcurrentHashMap.newKeySet();
    private final Set<GameInventoryGui> menus = ConcurrentHashMap.newKeySet();

    public GameBossbar newBossbar() {
        GameScope.requireActive(module);
        GameBossbar bar = new GameBossbar(module);
        bossbars.add(bar);
        return bar;
    }

    public GameInventoryGui newInventoryGui(int rows, Component title) {
        GameScope.requireActive(module);
        GameInventoryGui menu = new GameInventoryGui(module, rows, title, menus::remove);
        menus.add(menu);
        return menu;
    }

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
        if (sound.location() != null) {
            GameScope.requireLocation(module.world(), sound.location());
        }
        for (Player player : resolvePlayers(gamePlayers)) {
            Location source = sound.location() == null ? player.getLocation() : GameLocationHandle.toBukkit(sound.location(), module.world());
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
        bossbars.forEach(bar -> bar.hide(gamePlayer));
        Player player = resolvePlayer(gamePlayer);
        if (player == null) {
            return;
        }
        List.copyOf(menus).forEach(menu -> menu.closeViewer(player));
        player.sendTitlePart(TitlePart.TITLE, Component.empty());
        player.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
        player.sendActionBar(Component.empty());
        titleTracker.clear(player);
    }

    public void clear() {
        bossbars.forEach(GameBossbar::hide);
        bossbars.clear();
        List.copyOf(menus).forEach(GameInventoryGui::close);
        menus.clear();
        for (GameSidebar sidebar : List.copyOf(sidebars)) {
            sidebar.hide();
        }
        sidebars.clear();
        titleTracker.clear();
    }

    private Player resolvePlayer(GamePlayer gamePlayer) {
        if (gamePlayer == null) {
            return null;
        }
        GameScope.requirePlayer(module, gamePlayer);
        return GamePlayerHandle.bukkitPlayer(gamePlayer);
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

    private Component component(Component component) {
        return component == null ? Component.empty() : component;
    }
}
