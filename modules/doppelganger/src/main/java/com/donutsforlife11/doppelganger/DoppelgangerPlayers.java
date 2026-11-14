package com.donutsforlife11.doppelganger;

import java.util.Collections;
import java.util.List;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team.OptionStatus;

import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.event.GameEventHandler;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class DoppelgangerPlayers {
    private Doppelganger game;

    private GameTeam sheriffs;
    private GameTeam doppelgangers;

    public DoppelgangerPlayers(Doppelganger game) {
        this.game = game;
        this.sheriffs = game.teamManager().newTeam()
            .setColor(NamedTextColor.BLUE)
            .setDisplayName(Component.text("SHERIFF", NamedTextColor.BLUE, TextDecoration.BOLD))
            .setPrefix(Component.text("SHERIFF ", NamedTextColor.BLUE, TextDecoration.BOLD))
            .setNametagVisibility(OptionStatus.FOR_OWN_TEAM)
            .setTeamGlow(true)
            .setFriendlyFire(false)
            .setCollisionRule(OptionStatus.NEVER);
        this.doppelgangers = game.teamManager().newTeam()
            .setColor(NamedTextColor.RED)
            .setDisplayName(Component.text("DOPPELGANGER", NamedTextColor.RED, TextDecoration.BOLD))
            .setPrefix(Component.text("DOPPELGANGER ", NamedTextColor.RED, TextDecoration.BOLD))
            .setNametagVisibility(OptionStatus.FOR_OWN_TEAM)
            .setTeamGlow(true)
            .setFriendlyFire(false)
            .setCollisionRule(OptionStatus.NEVER);
    }

    public void assignTeams() {
        List<GamePlayer> players = List.copyOf(game.playerManager().getNonSpectators());
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            if (i < game.sheriffCount()) {
                sheriffs.addPlayer(players.get(i));
            } else {
                doppelgangers.addPlayer(players.get(i));
            }
        }
    }

    public void loadPlayers() {
        for (GamePlayer player : game.playerManager().getPlayers()) {
            player.addEffect(PotionEffectType.INVISIBILITY, -1, 0, true);
            player.setSprinting(false);
            player.teleport(game.world().getSpawnLocation());
        }
    }
    public void startPlayers() {
        assignTeams();
        List<GameLocation> spawns = game.spawns();
        int spawnCount = 0;
        for (GamePlayer player : game.playerManager().getPlayers()) {
            GameLocation spawn = spawns.get(spawnCount);
            player.teleport(spawn);
            player.setSpawnPoint(spawn);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            if (sheriffs.getPlayers().contains(player)) {
                player.addEffect(PotionEffectType.RESISTANCE, -1, 255, true);
                game.uiManager().title(player, Component.text(
                    "SHERIFF",
                    NamedTextColor.BLUE,
                    TextDecoration.BOLD
                ));
                game.uiManager().subtitle(player, Component.text("Catch and eliminate Doppelgangers!"));
            } else {
                game.uiManager().title(player, Component.text(
                    "DOPPELGANGER",
                    NamedTextColor.RED,
                    TextDecoration.BOLD
                ));
                game.uiManager().subtitle(player, Component.text("Blend in with and kill NPCs!"));
            }

            for (int i = 0; i < game.npcPerPlayer(); i++) {
                game.npcs().newNpc(spawn, player);
            }
            spawnCount = (spawnCount + 1) % spawns.size();
        }
    }

    @GameEventHandler
    public void onSheriffDamaged(GameEvent<EntityDamageByEntityEvent> event) {
        if (
            !(event.getEntity() instanceof GamePlayer victim && event.getDamager() instanceof GamePlayer)
        ) {
            return;
        }
        victim.setGlowing(true);
        game.uiManager().gameMessage(game.playerManager().getPlayers(), 
            Component.text("Sheriff " + victim.name() + " has been attacked and is now exposed!")
        );
    }

    public GameTeam sheriffs() {
        return sheriffs;
    }
    public GameTeam doppelgangers() {
        return doppelgangers;
    }
}
