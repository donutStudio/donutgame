package com.donutsforlife11.donutgame.internal.game;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UiManager;
import com.donutsforlife11.donutgame.api.ui.ValueFormatter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public abstract class GameModule {
    private Donutgame plugin;

    private MapManager mapManager;
    private PlayerManager playerManager;
    private UiManager uiManager;
    private TimeManager timeManager;
    private TeamManager teamManager;
    private BorderManager borderManager;
    private int index;
    private String id;
    private String name;

    private final Set<Listener> registeredListeners = new LinkedHashSet<>();

    private YamlConfiguration config;

    public void beforeLoad() {
    }

    public void onLoad() {
    }

    public void onStart() {

    }

    public void onUnload() {
    }

    protected final CompletableFuture<GameModule> startLoadSequence() {
        try {
            beforeLoad();
            onLoad();
            timeManager().newTimer(50).onFinish(titleTimer -> {
                uiManager().title(playerManager().getPlayers(), Component.text(name(), NamedTextColor.LIGHT_PURPLE));
                timeManager.newTimer(50).onFinish(ignoredTimer -> {
                    int countdownTime = 200;
                    ValueFormatter.countdown(timeManager(), countdownTime, formattedNumber -> {
                        uiManager().title(playerManager.getPlayers(), formattedNumber);
                    }).onFinish(ignored -> {
                        uiManager().title(playerManager.getPlayers(), Component.text("> START <").decorate(TextDecoration.BOLD));
                        onStart();
                    }).start();
                }).start();
            }).start();
            return CompletableFuture.completedFuture(this);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }
    protected final CompletableFuture<Void> shutdown() {
        return mapManager.unloadCurrentWorld();
    }
    protected final void registerEvents(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null!");
        }
        if (registeredListeners.add(listener)) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }
    protected final void unregisterEvents(Listener listener) {
        if (listener != null && registeredListeners.remove(listener)) {
            HandlerList.unregisterAll(listener);
        }
    }
    protected final void initialize(
        Donutgame plugin,
        GameModuleDescriptor descriptor, 
        int index, 
        PlayerManager playerManager, 
        MapManager mapManager,
        UiManager uiManager,
        TimeManager timeManager,
        TeamManager teamManager,
        BorderManager borderManager
    ) {
        this.plugin = plugin;
        this.index = index;
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.config = descriptor.createConfig();
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.uiManager = uiManager;
        this.timeManager = timeManager;
        this.teamManager = teamManager;
        this.borderManager = borderManager;
    }

    public String id() {
        return id;
    }
    public String name() {
        return name;
    }
    public YamlConfiguration config() {
        return config;
    }
    public int index() {
        return index;
    }
    public GameWorld world() {
        return mapManager().currentWorld();
    }
    public PlayerManager playerManager() {
        return playerManager;
    }
    public MapManager mapManager() {
        return mapManager;
    }
    public UiManager uiManager() {
        return uiManager;
    }
    public TimeManager timeManager() {
        return timeManager;
    }
    public TeamManager teamManager() {
        return teamManager;
    }
    public BorderManager borderManager() {
        return borderManager;
    }
}