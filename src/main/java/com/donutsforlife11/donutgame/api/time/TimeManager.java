package com.donutsforlife11.donutgame.api.time;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.Donutgame;

public class TimeManager {
    private final Donutgame plugin;
    private final Object taskLock = new Object();
    
    private final Set<GameTimer> timers = ConcurrentHashMap.newKeySet();

    private volatile BukkitTask tickerTask;

    public TimeManager(Donutgame plugin) {
        this.plugin = plugin;
    }

    public GameTimer newTimer(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("Timers cannot run for a negative amount of time!");
        }
        GameTimer timer = new GameTimer(this).setMaxTicks(time);
        timers.add(timer);
        return timer;
    }
    public GameTimer newTimer() {
        GameTimer timer = new GameTimer(this);
        timers.add(timer);
        return timer;
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
        stopTickerTaskIfIdle();
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
}
