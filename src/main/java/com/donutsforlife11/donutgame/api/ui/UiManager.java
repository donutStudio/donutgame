package com.donutsforlife11.donutgame.api.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.ui.sidebar.GameSidebar;
import com.donutsforlife11.donutgame.api.ui.title.TitlePacketTracker;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

public class UiManager {
    private final Donutgame plugin;
    private final GameModule module;

    private final TitlePacketTracker titlePacketTracker;
    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();

    public UiManager(GameModule module, Donutgame plugin) {
        this.module = module;
        this.plugin = plugin;
        this.titlePacketTracker = new TitlePacketTracker(this.plugin);
    }

    public void title(Player player, Component title) {
        title(Collections.singletonList(player), title);
    }
    public void title(Collection<Player> players, Component title) {
        Audience.audience(players).sendTitlePart(TitlePart.TITLE, title);
    }

    public void subtitle(Player player, Component subtitle) {
        subtitle(Collections.singletonList(player), subtitle);
    }
    public void subtitle(Collection<Player> players, Component subtitle) {
        for (Player player : players) {
            if (!titlePacketTracker.available() || titlePacketTracker.hasActiveTitle(player)) {
                player.sendTitlePart(TitlePart.SUBTITLE, subtitle);
                continue;
            }
            player.showTitle(Title.title(Component.empty(), subtitle));
        }
    }

    public void actionbar(Player player, Component actionbar) {
        actionbar(Collections.singletonList(player), actionbar);
    }
    public void actionbar(Collection<Player> players, Component actionbar) {
        Audience.audience(players).sendActionBar(actionbar);
    }

    public void chat(Player player, Component message) {
        chat(Collections.singletonList(player), message);
    }
    public void chat(Collection<Player> players, Component message) {
        Audience.audience(players).sendMessage(message);
    }

    public GameSidebar newSidebar() {
        GameSidebar sidebar = new GameSidebar(module, this);
        sidebars.add(sidebar);
        return sidebar;
    }
    public Set<GameSidebar> sidebars() {
        return sidebars;
    }
}
