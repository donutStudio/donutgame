package com.donutsforlife11.donutgame.api.time;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class TimeManager {
    private final GameModule module;
    private final Set<GameTimer> timers = ConcurrentHashMap.newKeySet();
    private final Object tickerLock = new Object();
    private BukkitTask tickerTask;

    public TimeManager(GameModule module) {
        this.module = module;
    }

    public GameTimer newTimer() {
        GameTimer timer = new GameTimer(this);
        timers.add(timer);
        return timer;
    }

    public GameTimer newTimer(int ticks) {
        return newTimer().setMaxTicks(ticks);
    }

    public void cancelAll() {
        for (GameTimer timer : Set.copyOf(timers)) {
            timer.cancel();
        }
        timers.clear();
        stopTicker();
    }

    void startTicking() {
        synchronized (tickerLock) {
            if (tickerTask != null) {
                return;
            }
            tickerTask = module.plugin().getServer().getScheduler().runTaskTimer(module.plugin(), this::tickTimers, 1L, 1L);
        }
    }

    void remove(GameTimer timer) {
        timers.remove(timer);
        stopTickingIfIdle();
    }

    void logTimerException(Throwable throwable) {
        module.plugin().getLogger().log(Level.SEVERE, "Game timer action failed in module " + module.id() + ":" + module.index() + ".", throwable);
    }

    private void tickTimers() {
        for (GameTimer timer : Set.copyOf(timers)) {
            timer.tick();
        }
        stopTickingIfIdle();
    }

    private void stopTickingIfIdle() {
        synchronized (tickerLock) {
            if (tickerTask == null || hasTickingTimer()) {
                return;
            }
            stopTicker();
        }
    }

    private void stopTicker() {
        synchronized (tickerLock) {
            if (tickerTask != null) {
                tickerTask.cancel();
                tickerTask = null;
            }
        }
    }

    private boolean hasTickingTimer() {
        for (GameTimer timer : timers) {
            if (timer.needsTicks()) {
                return true;
            }
        }
        return false;
    }
}
