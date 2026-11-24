package com.donutsforlife11.donutgame.internal.game;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.function.Supplier;
import java.nio.file.Path;

import org.bukkit.Sound;
import org.bukkit.event.HandlerList;
import org.bukkit.configuration.file.YamlConfiguration;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.border.BorderManager;
import com.donutsforlife11.donutgame.api.data.GameData;
import com.donutsforlife11.donutgame.api.storage.GameStorage;
import com.donutsforlife11.donutgame.internal.storage.StorageDatabase;
import com.donutsforlife11.donutgame.api.event.GameEventRegistrar;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;
import com.donutsforlife11.donutgame.api.ui.GameSound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public abstract class GameModule {
    private static final NamedTextColor[] COUNTDOWN_COLORS = {
        NamedTextColor.DARK_RED,
        NamedTextColor.RED,
        NamedTextColor.GOLD,
        NamedTextColor.YELLOW,
        NamedTextColor.GREEN
    };

    private Donutgame plugin;
    private MapManager mapManager;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private TimeManager timeManager;
    private UIManager uiManager;
    private BorderManager borderManager;
    private GameData data;
    private GameStorage storage;
    private StorageDatabase database;
    private Path directory;
    private GameEventRegistrar eventRegistrar;
    private String id;
    private String name;
    private int index;
    private YamlConfiguration config;
    private volatile boolean started;
    private volatile ModuleLifecycleState lifecycleState = ModuleLifecycleState.NEW;
    private final long handleGeneration = System.nanoTime();
    private CompletableFuture<Void> shutdownFuture;
    private final GameWorld world = new GameWorld(this);

    final void initialize(
        Donutgame plugin,
        GameModuleDescriptor descriptor,
        int index,
        YamlConfiguration config,
        MapManager mapManager,
        PlayerManager playerManager,
        TeamManager teamManager,
        TimeManager timeManager,
        UIManager uiManager,
        BorderManager borderManager
    ) {
        this.plugin = plugin;
        this.id = descriptor.id();
        this.name = descriptor.name();
        this.index = index;
        this.config = config;
        this.mapManager = mapManager;
        this.playerManager = playerManager;
        this.teamManager = teamManager;
        this.timeManager = timeManager;
        this.uiManager = uiManager;
        this.borderManager = borderManager;
        this.directory = descriptor.directory().toPath();
        this.data = new GameData(this, descriptor.directory());
        this.database = new StorageDatabase(directory.resolve("storage.db"));
        this.storage = new GameStorage(this, database);
        this.eventRegistrar = new GameEventRegistrar(this, plugin);
    }

    public void beforeLoad() {
        String explicitMap = config.getString("map");
        if (explicitMap != null && !explicitMap.isBlank()) {
            mapManager.load(explicitMap);
            return;
        }

        List<String> maps = config.getStringList("maps");
        if (!maps.isEmpty()) {
            mapManager.load(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        }
    }

    public void onLoad() {
    }

    public void onStart() {
    }

    public void onReload() {
    }

    public void onUnload() {
    }

    final CompletableFuture<GameModule> startLoadSequence(Supplier<CompletableFuture<?>> beforeCountdown) {
        try {
            requireState(ModuleLifecycleState.NEW);
            setLifecycleState(ModuleLifecycleState.LOADING);
            started = false;
            beforeLoad();
            return mapManager.whenReady()
                .thenCompose(ignored -> {
                    GameScope.requireActive(this);
                    return beforeCountdown.get();
                })
                .thenApply(ignored -> {
                    return enterCountdown(() -> {
                        onLoad();
                        registerGameEventHandlers(this);
                        setLifecycleState(ModuleLifecycleState.LOADED);
                    }, "load");
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null && acceptsLiveOperations()) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                    }
                });
        } catch (Throwable throwable) {
            setLifecycleState(ModuleLifecycleState.FAILED);
            return CompletableFuture.failedFuture(throwable);
        }
    }

    final CompletableFuture<Void> shutdown() {
        if (shutdownFuture != null) {
            return shutdownFuture;
        }
        shutdownFuture = new CompletableFuture<>();
        setLifecycleState(ModuleLifecycleState.UNLOADING);
        if (data != null) data.clearInteractions();
        if (database != null) {
            try {
                database.close();
            } catch (RuntimeException exception) {
                logError("Could not close game storage.", exception);
            }
        }
        borderManager.clear();
        timeManager.cancelAll();
        eventRegistrar.unregisterAll();
        HandlerList.unregisterAll(teamManager);
        playerManager.cleanupRuntimeState();
        playerManager.clear()
            .exceptionally(throwable -> {
                logError("Player cleanup failed while unloading. Continuing shutdown.", throwable);
                return null;
            })
            .thenCompose(ignored -> {
                teamManager.clear();
                return mapManager.unloadWorld();
            })
            .exceptionally(throwable -> {
                logError("World cleanup failed while unloading.", throwable);
                return null;
            })
            .whenComplete((ignored, throwable) -> {
                uiManager.clear();
                teamManager.clear();
                playerManager.cleanupRuntimeState();
                setLifecycleState(ModuleLifecycleState.UNLOADED);
            }).whenComplete((ignored, throwable) -> {
                if (throwable == null) {
                    shutdownFuture.complete(null);
                } else {
                    shutdownFuture.completeExceptionally(throwable);
                }
            });
        return shutdownFuture;
    }

    protected final CompletableFuture<GameModule> reload() {
        try {
            requireOneOf(ModuleLifecycleState.COUNTDOWN, ModuleLifecycleState.STARTED);
            setLifecycleState(ModuleLifecycleState.RELOADING);
            started = false;
            return mapManager.resetMap()
                .thenApply(ignored -> {
                    return enterCountdown(this::onReload, "reload");
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null && acceptsLiveOperations()) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                    }
                });
        } catch (Throwable throwable) {
            setLifecycleState(ModuleLifecycleState.FAILED);
            return CompletableFuture.failedFuture(throwable);
        }
    }

    protected final CompletableFuture<Void> unload() {
        return plugin.moduleService().unloadModule(index).thenAccept(ignored -> {});
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int index() {
        return index;
    }

    public YamlConfiguration config() {
        return config;
    }

    public GameData data() {
        return data;
    }

    public GameStorage storage() {
        return storage;
    }

    public Path directory() {
        return directory;
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public final GameWorld world() {
        return world;
    }

    public PlayerManager playerManager() {
        return playerManager;
    }

    public TeamManager teamManager() {
        return teamManager;
    }

    public TimeManager timeManager() {
        return timeManager;
    }

    public UIManager uiManager() {
        return uiManager;
    }

    public BorderManager borderManager() {
        return borderManager;
    }

    protected final void registerGameEventHandlers(Object target) {
        eventRegistrar.registerAnnotated(target);
    }

    public final ModuleLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public final long handleGeneration() {
        return handleGeneration;
    }

    public final boolean isHandleGenerationActive(long generation) {
        return generation == handleGeneration && acceptsLiveOperations();
    }

    public final boolean acceptsLiveOperations() {
        return switch (lifecycleState) {
            case UNLOADING, UNLOADED, FAILED -> false;
            default -> true;
        };
    }

    public final boolean hasStarted() {
        return started;
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

    private void requireState(ModuleLifecycleState expected) {
        if (lifecycleState != expected) {
            throw new IllegalStateException("Module " + id + " expected lifecycle state " + expected + " but was " + lifecycleState + ".");
        }
    }

    private void requireOneOf(ModuleLifecycleState... expectedStates) {
        for (ModuleLifecycleState expected : expectedStates) {
            if (lifecycleState == expected) {
                return;
            }
        }
        throw new IllegalStateException("Module " + id + " cannot perform this operation while " + lifecycleState + ".");
    }

    private void setLifecycleState(ModuleLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    private GameModule enterCountdown(ThrowingRunnable beforeCountdown, String transition) {
        try {
            if (!acceptsLiveOperations()) {
                throw new IllegalStateException("Module stopped before countdown.");
            }
            beforeCountdown.run();
            setLifecycleState(ModuleLifecycleState.COUNTDOWN);
            startCountdownSequence(transition);
            return this;
        } catch (Throwable throwable) {
            if (acceptsLiveOperations()) {
                setLifecycleState(ModuleLifecycleState.FAILED);
            }
            throw new CompletionException(throwable);
        }
    }

    private void startCountdownSequence(String transition) {
        startCountdown(config.getInt("countdown_ticks", 200), () -> {
            if (lifecycleState != ModuleLifecycleState.COUNTDOWN) {
                return;
            }
            try {
                onStart();
                started = true;
                setLifecycleState(ModuleLifecycleState.STARTED);
            } catch (Throwable throwable) {
                setLifecycleState(ModuleLifecycleState.FAILED);
                logError("Failed while starting module after " + transition + ".", throwable);
            }
        });
    }

    private void startCountdown(int countdownTicks, Runnable action) {
        timeManager.newTimer(50).onFinish(ignored -> {
            uiManager.title(playerManager.getOnlinePlayers(), Component.text(name, NamedTextColor.LIGHT_PURPLE));
            timeManager.newTimer(50).onFinish(ignoredTimer -> {
                sendCountdownTitle(countdownTicks, countdownTicks);
                timeManager.newTimer(countdownTicks)
                    .onTick(20, timer -> sendCountdownTitle(timer.remainingTicks(), timer.maxTicks()))
                    .onFinish(ignored2 -> {
                        uiManager.title(playerManager.getOnlinePlayers(), Component.text("> START <", NamedTextColor.WHITE, TextDecoration.BOLD));
                        uiManager.playSound(playerManager.getOnlinePlayers(), GameSound.of(Sound.BLOCK_NOTE_BLOCK_PLING).volume(0.9f).pitch(2f));
                        action.run();
                    })
                    .start();
            }).start();
        }).start();
    }

    private void sendCountdownTitle(int remainingTicks, int totalTicks) {
        int seconds = Math.max(1, (int) Math.ceil(Math.max(0, remainingTicks) / 20.0));
        Component title = Component.text(seconds, countdownColor(remainingTicks, totalTicks), TextDecoration.BOLD);
        uiManager.title(playerManager.getOnlinePlayers(), title);
        uiManager.playSound(playerManager.getOnlinePlayers(), GameSound.of(Sound.UI_BUTTON_CLICK).volume(0.7f).pitch(1.1f));
    }

    private NamedTextColor countdownColor(int remainingTicks, int totalTicks) {
        int totalSeconds = Math.max(0, totalTicks / 20);
        int elapsedSeconds = Math.round(Math.max(0, totalTicks - remainingTicks) / 20.0f);
        int baseBandLength = totalSeconds / COUNTDOWN_COLORS.length;
        int extraSeconds = totalSeconds % COUNTDOWN_COLORS.length;

        int colorIndex;
        if (baseBandLength <= 0) {
            colorIndex = Math.min(elapsedSeconds, COUNTDOWN_COLORS.length - 1);
        } else if (elapsedSeconds < baseBandLength + extraSeconds) {
            colorIndex = 0;
        } else {
            colorIndex = 1 + ((elapsedSeconds - (baseBandLength + extraSeconds)) / baseBandLength);
        }
        return COUNTDOWN_COLORS[Math.min(colorIndex, COUNTDOWN_COLORS.length - 1)];
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
