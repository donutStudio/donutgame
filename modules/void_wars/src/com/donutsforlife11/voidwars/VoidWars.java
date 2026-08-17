package com.donutsforlife11.voidwars;

import java.util.Collection;
import java.util.stream.Collectors;

import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.border.GameBorder;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class VoidWars extends GameModule {
    public int maxRounds;
    public int teamSize;
    public int baseRespawnTime;
    public boolean protectRegions;
    public int pvpEnablementTime;
    public int groundCollapseTime;
    public int minChests;
    public int maxChests;

    private int round = 0;
    private boolean roundStarted = false;
    public final String spawnPointName = "spawn";
    public final String mutableRegionName = "mutable_zone";
    public final String spawnPlatformName = "spawn_platform";
    public final String borderRegionName = "starting_border";
    public final String eventConfig = "events.yml";
    public final String itemDropsKey = "item_drops";
    public final String borderShrinksKey = "border_shrinks";
    public final String chestFillsKey = "chest_fills";

    private VoidWarsPlayers voidWarsPlayers = new VoidWarsPlayers(this);
    private VoidWarsSidebar voidWarsSidebar;
    private VoidWarsRegionProtection voidWarsRegionProtection = new VoidWarsRegionProtection(this);
    private VoidWarsEvents voidWarsEvents = new VoidWarsEvents(this);
    private GameTimer mainTimer = null;
    private GameBorder mainBorder = null;
    private boolean roundEnding = false;

    @Override
    public void onLoad() {

        maxRounds = config().getInt("max_rounds");
        teamSize = config().getInt("team_size");
        baseRespawnTime = config().getInt("base_respawn_time") * 20;
        protectRegions = config().getBoolean("protect_regions");
        pvpEnablementTime = config().getInt("pvp_enablement_time") * 20;
        groundCollapseTime = config().getInt("ground_collapse_time") * 20;
        minChests = config().getInt("min_chests");
        maxChests = config().getInt("max_chests");

        voidWarsPlayers.assignTeams();
        playerManager().onPlayerRegistered(player -> {
            voidWarsPlayers.setupPlayer(player, false);
        });
        registerEventHandlers(voidWarsPlayers);
        registerEventHandlers(voidWarsRegionProtection);
        voidWarsEvents.populateEventLists();
        loadRound();
        voidWarsSidebar = new VoidWarsSidebar(this);
        voidWarsSidebar.createSidebar();
    }

    @Override
    public void onStart() {
        startRound();
    }

    @Override
    public void onReload() {
        loadRound();
    }

    public void loadRound() {
        roundStarted = false;
        roundEnding = false;
        round++;
        mainTimer = timeManager().newTimer();
        mainBorder = borderManager().newBorder(world().getRegion(borderRegionName));
        world().setPvp(false);
        world().setFallDamage(false);
        world().setHungerEnabled(false);
        for (GamePlayer player : playerManager().getPlayers()) {
            voidWarsPlayers.setupPlayer(player, true);
            player.addEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true);
            world().setHungerEnabled(false);
        }
        voidWarsEvents.addEventActions();
    }
    public void startRound() {
        roundStarted = true;
        roundEnding = false;
        mainTimer.start();
        for (GamePlayer player : playerManager().getPlayers()) {
            player.removeEffect(PotionEffectType.INVISIBILITY);
        }
        world().setHungerEnabled(true);
        world().setFallDamage(true);
    }
    public void endRound(Collection<GamePlayer> winners) {
        if (roundEnding || !roundStarted) {
            return;
        }
        roundEnding = true;
        roundStarted = false;
        GameTimer timer = mainTimer;
        mainTimer = null;
        if (timer != null) {
            timer.cancel();
        }
        GameBorder border = mainBorder;
        mainBorder = null;
        if (border != null) {
            border.remove();
        }
        timeManager().newTimer(round >= maxRounds ? 100 : 50).onFinish(ignored -> {
            uiManager().title(playerManager().getSpectators(), Component.text("Round Over!"));
            uiManager().playSound(playerManager().getSpectators(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.5f);
            uiManager().title(winners, Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD));
            uiManager().playSound(winners, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.675f);
            if (!winners.isEmpty()) {
                String label = winners.size() == 1 ? "Winner: " : "Winners: ";
                uiManager().subtitle(playerManager().getPlayers(),
                Component.text(label + winners.stream().map(player -> player.getName()).collect(Collectors.joining(", "))));
            }
            for (GamePlayer player : winners) {
                player.setSpectator(true, player.location());
            }
            if (round >= maxRounds) {
                uiManager().title(playerManager().getPlayers(), Component.text("Game over!", NamedTextColor.WHITE, TextDecoration.BOLD));
                unload();
            } else {
                timeManager().newTimer(100).onFinish(ignored2 -> {
                    reload();
                }).start();
            }
        }).start();

    }

    public GameTimer mainTimer() {
        return mainTimer;
    }
    public GameBorder mainBorder() {
        return mainBorder;
    }

    public VoidWarsPlayers voidWarsPlayers() {
        return voidWarsPlayers;
    }
    public VoidWarsSidebar voidWarsSidebar() {
        return voidWarsSidebar;
    }
    public VoidWarsRegionProtection voidWarsRegionProtection() {
        return voidWarsRegionProtection;
    }
    public VoidWarsEvents voidWarsEvents() {
        return voidWarsEvents;
    }

    public int round() {
        return round;
    }
    public boolean roundStarted() {
        return roundStarted;
    }
}
