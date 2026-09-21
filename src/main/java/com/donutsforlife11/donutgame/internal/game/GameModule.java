package com.donutsforlife11.donutgame.internal.game;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.function.Supplier;

import org.bukkit.Sound;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEventAdapterRegistry;
import com.donutsforlife11.donutgame.api.event.GameEventRegistrar;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.api.object.BlockSpec;
import com.donutsforlife11.donutgame.api.object.EntitySpec;
import com.donutsforlife11.donutgame.api.item.ItemSpec;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.PlayerManager;
import com.donutsforlife11.donutgame.api.team.TeamManager;
import com.donutsforlife11.donutgame.api.time.TimeManager;
import com.donutsforlife11.donutgame.api.ui.UIManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public abstract class GameModule {
    public static final String DEFAULT_WORLD_ID = "default";
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
    private GameEventRegistrar eventRegistrar;
    private String id;
    private String name;
    private int index;
    private YamlConfiguration config;
    private volatile boolean started;
    private volatile ModuleLifecycleState lifecycleState = ModuleLifecycleState.NEW;
    private final Map<String, GameWorld> worlds = new LinkedHashMap<>();
    private final Set<Listener> registeredListeners = new LinkedHashSet<>();

    protected GameModule() {
        worlds.put(DEFAULT_WORLD_ID, new GameWorld(DEFAULT_WORLD_ID));
    }

    protected final void initialize(
        Donutgame plugin,
        GameModuleDescriptor descriptor,
        int index,
        YamlConfiguration config,
        MapManager mapManager,
        PlayerManager playerManager,
        TeamManager teamManager,
        TimeManager timeManager,
        UIManager uiManager
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
        this.eventRegistrar = new GameEventRegistrar(this);
    }

    public void beforeLoad() {
        String explicitMap = config.getString("map");
        if (explicitMap != null && !explicitMap.isBlank()) {
            mapManager.setMap(explicitMap);
            return;
        }

        List<String> maps = config.getStringList("maps");
        if (!maps.isEmpty()) {
            mapManager.setMap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        }
    }

    public void onLoad() {
    }

    public void onStart() {
    }

    public void onUnload() {
    }

    protected final CompletableFuture<GameModule> startLoadSequence() {
        return startLoadSequence(() -> CompletableFuture.completedFuture(null));
    }

    protected final CompletableFuture<GameModule> startLoadSequence(Supplier<CompletableFuture<?>> beforeCountdown) {
        try {
            requireState(ModuleLifecycleState.NEW);
            setLifecycleState(ModuleLifecycleState.LOADING);
            started = false;
            beforeLoad();
            return mapManager.whenReady()
                .thenCompose(ignored -> {
                    try {
                        onLoad();
                        registerGameEventHandlers(this);
                        registerFieldGameEventHandlers();
                        CompletableFuture<?> beforeCountdownFuture = beforeCountdown == null
                            ? CompletableFuture.completedFuture(null)
                            : beforeCountdown.get();
                        return beforeCountdownFuture.thenCompose(beforeCountdownIgnored -> {
                            setLifecycleState(ModuleLifecycleState.COUNTDOWN);
                            return startCountdownSequence();
                        });
                    } catch (Throwable throwable) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                        return CompletableFuture.failedFuture(throwable);
                    }
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        setLifecycleState(ModuleLifecycleState.FAILED);
                    }
                });
        } catch (Throwable throwable) {
            setLifecycleState(ModuleLifecycleState.FAILED);
            return CompletableFuture.failedFuture(throwable);
        }
    }

    protected final CompletableFuture<Void> shutdown() {
        setLifecycleState(ModuleLifecycleState.UNLOADING);
        timeManager.cancelAll();
        unregisterDynamicEvents();
        teamManager.clear();
        return playerManager.clear()
            .thenCompose(ignored -> mapManager.unloadWorlds())
            .whenComplete((ignored, throwable) -> {
                uiManager.clear();
                setLifecycleState(throwable == null ? ModuleLifecycleState.UNLOADED : ModuleLifecycleState.FAILED);
            });
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

    public int index() {
        return index;
    }

    public YamlConfiguration config() {
        return config;
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public GameWorld world() {
        return defaultWorld();
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

    protected final void setBlock(GameLocation location, BlockSpec<?> block) {
        world().setBlock(location, block);
    }

    protected final void fill(GameRegion region, BlockSpec<?> block) {
        world().fillBlocks(region, block);
    }

    protected final void give(GamePlayer player, ItemSpec item) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        player.give(item);
    }

    protected final GameEntity summon(GameLocation location, EntitySpec<?> entity) {
        return world().summon(location, entity);
    }

    public final Map<String, GameWorld> worlds() {
        return Collections.unmodifiableMap(worlds);
    }

    public final GameWorld defaultWorld() {
        return worlds.get(DEFAULT_WORLD_ID);
    }

    protected final void putWorld(GameWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        // Implement later: Multi-world modules: remove this guard once a module can intentionally own
        // multiple GameWorlds and expose a public API for selecting between them.
        if (!DEFAULT_WORLD_ID.equals(world.id())) {
            throw new UnsupportedOperationException("Modules are limited to one GameWorld for now.");
        }
        worlds.put(world.id(), world);
    }

    protected final void registerGameEventHandlers(Object target) {
        eventRegistrar.registerAnnotated(target);
    }

    protected final GameEventAdapterRegistry gameEventAdapters() {
        return eventRegistrar.adapterRegistry();
    }

    public final void registerDynamicEvent(
        Listener listener,
        Class<? extends Event> eventType,
        EventExecutor executor,
        EventPriority priority,
        boolean ignoreCancelled
    ) {
        if (listener == null || eventType == null || executor == null) {
            throw new IllegalArgumentException("listener, eventType, and executor cannot be null");
        }
        if (registeredListeners.add(listener)) {
            plugin.getServer().getPluginManager().registerEvent(eventType, listener, priority, executor, plugin, ignoreCancelled);
        }
    }

    public final ModuleLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public final boolean hasStarted() {
        return started;
    }

    public final void log(String message) {
        plugin.getLogger().info("[" + id + ":" + index + "] " + message);
    }

    public final void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, "[" + id + ":" + index + "] " + message, throwable);
    }

    private void requireState(ModuleLifecycleState expected) {
        if (lifecycleState != expected) {
            throw new IllegalStateException("Module " + id + " expected lifecycle state " + expected + " but was " + lifecycleState + ".");
        }
    }

    private void setLifecycleState(ModuleLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    private CompletableFuture<GameModule> startCountdownSequence() {
        CompletableFuture<GameModule> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean();
        startCountdown(config.getInt("countdown_ticks", 200), () -> {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            try {
                onStart();
                started = true;
                setLifecycleState(ModuleLifecycleState.STARTED);
                future.complete(this);
            } catch (Throwable throwable) {
                setLifecycleState(ModuleLifecycleState.FAILED);
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void startCountdown(int countdownTicks, Runnable action) {
        timeManager.newTimer(50).onFinish(ignored -> {
            for (GamePlayer player : playerManager.getOnlinePlayers()) {
                player.title(Component.text(name, NamedTextColor.LIGHT_PURPLE));
            }
            timeManager.newTimer(50).onFinish(ignoredTimer -> {
                sendCountdownTitle(countdownTicks, countdownTicks);
                timeManager.newTimer(countdownTicks)
                    .onTick(20, timer -> sendCountdownTitle(timer.remainingTicks(), timer.maxTicks()))
                    .onFinish(ignored2 -> {
                        for (GamePlayer player : playerManager.getOnlinePlayers()) {
                            player.title(Component.text("> START <", NamedTextColor.WHITE, TextDecoration.BOLD));
                            player.playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 2f);
                        }
                        action.run();
                    })
                    .start();
            }).start();
        }).start();
    }

    private void sendCountdownTitle(int remainingTicks, int totalTicks) {
        int seconds = Math.max(1, (int) Math.ceil(Math.max(0, remainingTicks) / 20.0));
        Component title = Component.text(seconds, countdownColor(remainingTicks, totalTicks), TextDecoration.BOLD);
        for (GamePlayer player : playerManager.getOnlinePlayers()) {
            player.title(title);
            player.playSound(Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        }
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

    private void unregisterDynamicEvents() {
        for (Listener listener : List.copyOf(registeredListeners)) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    private void registerFieldGameEventHandlers() {
        for (Class<?> current = getClass(); current != null && current != GameModule.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object target = fieldValue(field);
                if (target != null && target != this) {
                    registerGameEventHandlers(target);
                }
            }
        }
    }

    private Object fieldValue(Field field) {
        try {
            field.setAccessible(true);
            return field.get(this);
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }
}
