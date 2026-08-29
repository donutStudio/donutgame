package com.donutsforlife11.donutgame.api.time;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.internal.game.ResourceScope;

public class TimeManager {
    private final Donutgame plugin;
    private final Object taskLock = new Object();
    
    private final Set<GameTimer> timers = ConcurrentHashMap.newKeySet();
    private final Map<GameTimer, ResourceScope> timerScopes = new ConcurrentHashMap<>();

    private volatile BukkitTask tickerTask;

    public TimeManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public GameTimer newTimer(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("Timers cannot run for a negative amount of time!");
        }
        return newTimer(time, ResourceScope.ROUND);
    }

    public GameTimer newTimer(int time, ResourceScope scope) {
        if (time < 0) {
            throw new IllegalArgumentException("Timers cannot run for a negative amount of time!");
        }
        return track(new GameTimer(this).setMaxTicks(time), scope);
    }

    public GameTimer newTimer() {
        return newTimer(ResourceScope.ROUND);
    }

    public GameTimer newTimer(ResourceScope scope) {
        return track(new GameTimer(this), scope);
    }

    public GameTimer newModuleTimer(int time) {
        return newTimer(time, ResourceScope.MODULE);
    }

    public GameTimer newModuleTimer() {
        return newTimer(ResourceScope.MODULE);
    }

    Set<GameTimer> timers() {
        return timers;
    }

    void activate(GameTimer timer) {
        timers.add(timer);
        synchronized (taskLock) {
            if (tickerTask != null || timers.isEmpty()) {
                return;
            }

            tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimers, 1L, 1L);
        }
    }
    void deactivate(GameTimer timer) {
        timers.remove(timer);
        timerScopes.remove(timer);
        stopTickerTaskIfIdle();
    }

    public void cancelRoundScoped() {
        for (Map.Entry<GameTimer, ResourceScope> entry : Map.copyOf(timerScopes).entrySet()) {
            if (entry.getValue() == ResourceScope.ROUND) {
                entry.getKey().cancel();
            }
        }
    }

    public void cancelAll() {
        for (GameTimer timer : Set.copyOf(timers)) {
            timer.cancel();
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
        for (GameTimer timer : timers) {
            timer.tick();
        }
        stopTickerTaskIfIdle();
    }

    private GameTimer track(GameTimer timer, ResourceScope scope) {
        timers.add(timer);
        timerScopes.put(timer, scope == null ? ResourceScope.ROUND : scope);
        return timer;
    }

    void logTimerException(Throwable exception) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Game timer action failed.", exception);
    }
}
