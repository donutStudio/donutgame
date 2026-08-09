package com.donutsforlife11.donutgame.internal.game;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.data.GameData;
import com.donutsforlife11.donutgame.api.event.GameEventRegistrar;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UiManager;
import com.donutsforlife11.donutgame.api.ui.ValueFormatter;
import com.donutsforlife11.donutgame.internal.item.GameItemService;

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
    private GameEventRegistrar eventRegistrar;
    private GameData data;
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
            return CompletableFuture.completedFuture(this);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    protected final CompletableFuture<Void> shutdown() {
        timeManager.cancelAll();
        uiManager.clear();
        for (Listener listener : registeredListeners.toArray(Listener[]::new)) {
            unregisterEvents(listener);
        }
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

    public final void registerDynamicEvent(Listener listener, Class<? extends Event> eventType, EventExecutor executor) {
        if (listener == null || eventType == null || executor == null) {
            throw new IllegalArgumentException("Listener, event type, and executor cannot be null.");
        }
        if (registeredListeners.add(listener)) {
            plugin.getServer().getPluginManager().registerEvent(eventType, listener, EventPriority.NORMAL, executor, plugin);
        }
    }

    protected final void initialize(
        Donutgame plugin,
        GameModuleDescriptor descriptor,
        int index,
        YamlConfiguration config,
        PlayerManager playerManager,
        MapManager mapManager,
        UiManager uiManager,
        TimeManager timeManager,
        TeamManager teamManager,
        BorderManager borderManager,
        GameItemService itemService
    ) {
        this.plugin = plugin;
        this.index = index;
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.config = config;
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.uiManager = uiManager;
        this.timeManager = timeManager;
        this.teamManager = teamManager;
        this.borderManager = borderManager;
        this.eventRegistrar = new GameEventRegistrar(this);
        this.data = new GameData(this, itemService);
    }

    protected final void startCountdown(int countdownTicks, Runnable action) {
        timeManager().newTimer(50).onFinish(ignored -> {
            uiManager().title(playerManager().getPlayers(), Component.text(name(), NamedTextColor.LIGHT_PURPLE));
            timeManager().newTimer(50).onFinish(ignoredTimer -> {
                uiManager().title(playerManager().getPlayers(), Component.text(countdownTicks / 20, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
                uiManager().sound(playerManager().getPlayers(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                ValueFormatter.countdown(timeManager(), countdownTicks, formattedNumber -> {
                    uiManager().title(playerManager().getPlayers(), formattedNumber);
                    uiManager().sound(playerManager().getPlayers(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                }).onFinish(ignored2 -> {
                    uiManager().title(playerManager().getPlayers(), Component.text("> START <").decorate(TextDecoration.BOLD));
                    uiManager().sound(playerManager().getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    action.run();
                }).start();
            }).start();
        }).start();
    }

    public CompletableFuture<Boolean> unloadSelf() {
        return plugin.moduleService().unloadModule(index);
    }

    public InputStream resource(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    public final Donutgame plugin() {
        return plugin;
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

    public GameData data() {
        return data;
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

    public GameEventRegistrar events() {
        return eventRegistrar;
    }

    public final void log(String message) {
        plugin.getLogger().info("[" + id + ":" + index + "] " + message);
    }

    public final void logWarning(String message) {
        plugin.getLogger().warning("[" + id + ":" + index + "] " + message);
    }

    public final void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, "[" + id + ":" + index + "] " + message, throwable);
    }
}
