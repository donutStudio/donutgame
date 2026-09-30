package com.donutsforlife11.donutgame.internal.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SpectatorService {
    private final Donutgame plugin;

    public SpectatorService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public void enable() {
    }

    public void disable() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player hidden : Bukkit.getOnlinePlayers()) {
                viewer.showEntity(plugin, hidden);
                viewer.showPlayer(plugin, hidden);
            }
        }
    }

    public void showSpectator(GamePlayer spectator) {
        Player player = spectator == null ? null : spectator.bukkitPlayer();
        if (player == null) {
            return;
        }
        player.playerListName(spectatorDisplayName(spectator, player));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.hideEntity(plugin, player);
            }
            listRealPlayer(viewer, player);
        }
    }

    public void hideSpectator(GamePlayer spectator) {
        Player player = spectator == null ? null : spectator.bukkitPlayer();
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showEntity(plugin, player);
            listRealPlayer(viewer, player);
        }
    }

    public void refreshForViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        for (GameModule game : plugin.moduleService().activeGames().values()) {
            for (GamePlayer spectator : game.playerManager().getSpectators()) {
                Player player = spectator.bukkitPlayer();
                if (player == null) {
                    continue;
                }
                if (!viewer.equals(player)) {
                    viewer.hideEntity(plugin, player);
                }
                listRealPlayer(viewer, player);
            }
        }
    }

    public boolean isSpectator(Player player) {
        GameModule game = plugin.moduleService().getGameOfPlayer(player);
        GamePlayer gamePlayer = game == null ? null : game.playerManager().getPlayer(player);
        return gamePlayer != null && gamePlayer.isSpectator();
    }

    private void listRealPlayer(Player viewer, Player player) {
        if (viewer == null || player == null || !viewer.canSee(player)) {
            return;
        }
        try {
            viewer.listPlayer(player);
        } catch (IllegalArgumentException ignored) {
            // Paper requires the viewer to be able to see the player before listing them.
        }
    }

    private Component spectatorDisplayName(GamePlayer spectator, Player player) {
        Component baseName = Component.text(player.getName(), NamedTextColor.GRAY);
        var team = spectator.getTeam();
        if (team != null) {
            baseName = team.prefix()
                .append(Component.text(player.getName(), team.color()))
                .append(team.suffix());
        }
        return baseName;
    }
}
