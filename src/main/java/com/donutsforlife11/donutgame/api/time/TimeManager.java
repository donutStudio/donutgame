package com.donutsforlife11.donutgame.api.time;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class TimeManager {
    private final Plugin plugin;
    private final Set<GameTimer> timers = ConcurrentHashMap.newKeySet();

    private final Object taskLock = new Object();

    private volatile BukkitTask tickerTask;
    private volatile boolean shutdown;

    public TimeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public GameTimer createTimer(int time) {
        ensureActive();
        return new GameTimer(this, time);
    }

    public GameTimer createTimer() {
        ensureActive();
        return new GameTimer(this, -1);
    }

    public GameTimer formattedCountdown(int countdownTime, Consumer<Component> action) {
        ensureActive();
        NamedTextColor[] countdownColors = 
            {NamedTextColor.DARK_RED, NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN};
        int totalSeconds = countdownTime / 20;
        int baseBandLength = totalSeconds / countdownColors.length;
        int extraSeconds = totalSeconds % countdownColors.length;

        GameTimer countdownTimer = createTimer(countdownTime).whileRunning(20, countdown -> {
            int elapsedSeconds = countdown.getElapsedSeconds();
            int colorIndex;
            if (elapsedSeconds < baseBandLength + extraSeconds) {
                colorIndex = 0;
            } else {
                colorIndex = 1 + ((elapsedSeconds - (baseBandLength+ extraSeconds)) / baseBandLength);
            }
            colorIndex = Math.min(colorIndex, countdownColors.length - 1);

            Component formattedNumber = Component.text(countdown.getRemainingSeconds() + 1, countdownColors[colorIndex]).decorate(TextDecoration.BOLD);
            action.accept(formattedNumber);
        });

        return countdownTimer;
    }

    void activate(GameTimer timer) {
        ensureActive();
        timers.add(timer);
        ensureTickerTask();
    }

    void deactivate(GameTimer timer) {
        timers.remove(timer);
        stopTickerTaskIfIdle();
    }

    void logCallbackFailure(String callbackType, Throwable throwable) {
        plugin.getLogger().severe("Unhandled exception in GameTimer " + callbackType + " callback:");
        throwable.printStackTrace();
    }

    public void shutdown() {
        shutdown = true;

        BukkitTask task = tickerTask;
        if (task != null) {
            task.cancel();
            tickerTask = null;
        }

        List.copyOf(timers).forEach(GameTimer::cancelFromManager);
        timers.clear();
    }

    private void ensureActive() {
        if (shutdown || !plugin.isEnabled()) {
            throw new IllegalStateException("TimeManager is not active.");
        }
    }

    private void ensureTickerTask() {
        synchronized (taskLock) {
            if (tickerTask != null || timers.isEmpty()) {
                return;
            }

            tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimers, 1L, 1L);
        }
    }

    private void stopTickerTaskIfIdle() {
        synchronized (taskLock) {
            if (!timers.isEmpty() || tickerTask == null) {
                return;
            }

            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void tickTimers() {
        if (shutdown || !plugin.isEnabled()) {
            shutdown();
            return;
        }

        for (GameTimer timer : List.copyOf(timers)) {
            timer.tick();
        }

        stopTickerTaskIfIdle();
    }
}
