package com.donutsforlife11.donutgame.internal.game;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
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
import com.donutsforlife11.donutgame.api.map.GameMap;
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
    private volatile boolean started;
    private volatile boolean transitioning;

    private final Set<Listener> registeredListeners = new LinkedHashSet<>();

    private YamlConfiguration config;

    public void beforeLoad() {
        List<String> maps = config().getStringList("maps");
        if (!maps.isEmpty()) {
            mapManager().setMap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        } else {
            mapManager.setMap(MapManager.DEFAULT_MAP_ID);
        }
    }

    public void onLoad() {
    }

    public void onReload() {
        onLoad();
    }

    public void onStart() {
    }

    public void onUnload() {
    }

    protected final CompletableFuture<GameModule> startLoadSequence(Runnable beforeCountdown) {
        try {
            started = false;
            beforeLoad();
            return mapManager.whenReady().thenCompose(ignored -> {
                try {
                    if (beforeCountdown != null) {
                        beforeCountdown.run();
                    }
                    onLoad();
                    eventRegistrar.registerAnnotated(this);
                    startCountdownSequence().exceptionally(throwable -> {
                        logError("Start sequence failed.", throwable);
                        return null;
                    });
                    return CompletableFuture.completedFuture(this);
                } catch (Throwable throwable) {
                    return CompletableFuture.failedFuture(throwable);
                }
            });
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public void reload() {
        try {
            started = false;
            mapManager.resetMap()
                .thenCompose(ignored -> {
                    try {
                        onReload();
                        return startCountdownSequence();
                    } catch (Throwable throwable) {
                        return CompletableFuture.failedFuture(throwable);
                    }
                })
                .exceptionally(throwable -> {
                logError("Reload start sequence failed.", throwable);
                return null;
            });
        } catch (Throwable throwable) {
            logError("Reload failed.", throwable);
        }
    }

    protected final CompletableFuture<Void> shutdown() {
        transitioning = true;
        return runSync(() -> {
            timeManager.cancelAll();
            for (Listener listener : registeredListeners.toArray(Listener[]::new)) {
                unregisterEvents(listener);
            }
            uiManager.resetPlayerStateForShutdown();
            uiManager.clear();
        }).thenCompose(ignored -> playerManager.evacuateForShutdown())
            .thenCompose(ignored -> runSync(() -> {
                teamManager.clear();
                playerManager.clearForShutdown();
            }))
            .thenCompose(ignored -> mapManager.unloadWorld())
            .whenComplete((ignored, throwable) -> {
                transitioning = false;
            });
    }

    private CompletableFuture<Void> runSync(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else plugin.getServer().getScheduler().runTask(plugin, task);
        return future;
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

    protected final void registerEventHandlers(Object target) {
        eventRegistrar.registerAnnotated(target);
    }

    public final void registerDynamicEvent(Listener listener, Class<? extends Event> eventType, EventExecutor executor) {
        registerDynamicEvent(listener, eventType, executor, EventPriority.NORMAL, false);
    }

    public final void registerDynamicEvent(Listener listener, Class<? extends Event> eventType, EventExecutor executor, EventPriority priority, boolean ignoreCancelled) {
        if (listener == null || eventType == null || executor == null) {
            throw new IllegalArgumentException("Listener, event type, and executor cannot be null.");
        }
        if (registeredListeners.add(listener)) {
            plugin.getServer().getPluginManager().registerEvent(eventType, listener, priority, executor, plugin, ignoreCancelled);
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
                uiManager().playSound(playerManager().getPlayers(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                ValueFormatter.countdown(timeManager(), countdownTicks, formattedNumber -> {
                    uiManager().title(playerManager().getPlayers(), formattedNumber);
                    uiManager().playSound(playerManager().getPlayers(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                }).onFinish(ignored2 -> {
                    uiManager().title(playerManager().getPlayers(), Component.text("> START <").decorate(TextDecoration.BOLD));
                    uiManager().playSound(playerManager().getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 2f);
                    action.run();
                }).start();
            }).start();
        }).start();
    }

    private CompletableFuture<GameModule> startCountdownSequence() {
        CompletableFuture<GameModule> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean();
        startCountdown(config().getInt("countdown_ticks", 200), () -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            try {
                onStart();
                started = true;
                playerManager.activatePostStartPlayers();
                future.complete(this);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> unload() {
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

    public YamlConfiguration config(String path) {
        InputStream stream = resource(path);
        if (stream == null) {
            throw new IllegalStateException("Missing module configuration " + path);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load module configuration " + path, e);
        }
    }

    public GameData data() {
        return data;
    }

    public int index() {
        return index;
    }

    public GameWorld world() {
        return mapManager().world();
    }
    public GameMap currentMap() {
        return mapManager.map();
    }

    public final boolean isTransitioning() {
        return transitioning;
    }

    public final boolean hasStarted() {
        return started;
    }

    public final void setTransitioning(boolean transitioning) {
        this.transitioning = transitioning;
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
