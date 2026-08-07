package com.donutsforlife11.donutgame.api.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
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

    public void title(GamePlayer player, Component title) {
        if (player.player() != null) {
            title(player.player(), title);
        }
    }

    public void title(Collection<Player> players, Component title) {
        Audience.audience(players).sendTitlePart(TitlePart.TITLE, title);
    }

    public void titlePlayers(Collection<GamePlayer> players, Component title) {
        title(resolvePlayers(players), title);
    }

    public void subtitle(Player player, Component subtitle) {
        subtitle(Collections.singletonList(player), subtitle);
    }

    public void subtitle(GamePlayer player, Component subtitle) {
        if (player.player() != null) {
            subtitle(player.player(), subtitle);
        }
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

    public void subtitlePlayers(Collection<GamePlayer> players, Component subtitle) {
        subtitle(resolvePlayers(players), subtitle);
    }

    public void actionbar(Player player, Component actionbar) {
        actionbar(Collections.singletonList(player), actionbar);
    }

    public void actionbar(GamePlayer player, Component actionbar) {
        if (player.player() != null) {
            actionbar(player.player(), actionbar);
        }
    }

    public void actionbar(Collection<Player> players, Component actionbar) {
        Audience.audience(players).sendActionBar(actionbar);
    }

    public void actionbarPlayers(Collection<GamePlayer> players, Component actionbar) {
        actionbar(resolvePlayers(players), actionbar);
    }

    public void chat(Player player, Component message) {
        chat(Collections.singletonList(player), message);
    }

    public void chat(GamePlayer player, Component message) {
        if (player.player() != null) {
            chat(player.player(), message);
        }
    }

    public void chat(Collection<Player> players, Component message) {
        Audience.audience(players).sendMessage(message);
    }

    public void chatPlayers(Collection<GamePlayer> players, Component message) {
        chat(resolvePlayers(players), message);
    }

    public GameSidebar newSidebar() {
        GameSidebar sidebar = new GameSidebar(module, this);
        sidebars.add(sidebar);
        return sidebar;
    }

    public Set<GameSidebar> sidebars() {
        return sidebars;
    }

    @SuppressWarnings("null")
    private Collection<Player> resolvePlayers(Collection<GamePlayer> players) {
        return players.stream()
            .map(GamePlayer::player)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
