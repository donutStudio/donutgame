package com.donutsforlife11.donutgame.api.time;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.internal.game.GameModule;

public class TimeManager {
    private final GameModule module;
    private final Set<GameTimer> timers = ConcurrentHashMap.newKeySet();
    private final PriorityQueue<ScheduledTimer> scheduledTimers = new PriorityQueue<>();
    private final Object tickerLock = new Object();
    private BukkitTask tickerTask;
    private long tickerWakeTick = Long.MAX_VALUE;

    public TimeManager(GameModule module) {
        this.module = module;
    }

    public GameTimer newTimer() {
        return new GameTimer(this);
    }

    public GameTimer newTimer(int ticks) {
        return newTimer().setMaxTicks(ticks);
    }

    public void cancelAll() {
        for (GameTimer timer : Set.copyOf(timers)) {
            timer.cancel();
        }
        timers.clear();
        synchronized (tickerLock) {
            scheduledTimers.clear();
        }
        stopTicker();
    }

    long currentTick() {
        return Bukkit.getCurrentTick();
    }

    void schedule(GameTimer timer) {
        long now = currentTick();
        long wakeTick = timer.nextWakeTick(now);
        synchronized (tickerLock) {
            if (!timers.contains(timer)) {
                return;
            }
            long version = timer.assignSchedule(wakeTick);
            if (wakeTick != Long.MAX_VALUE) {
                scheduledTimers.add(new ScheduledTimer(timer, wakeTick, version));
            }
            scheduleNextTicker(now);
        }
    }

    void register(GameTimer timer) {
        timers.add(timer);
        schedule(timer);
    }

    void remove(GameTimer timer) {
        timers.remove(timer);
        timer.clearSchedule();
        synchronized (tickerLock) {
            scheduleNextTicker(currentTick());
        }
    }

    void logTimerException(Throwable throwable) {
        module.plugin().getLogger().log(Level.SEVERE, "Game timer action failed in module " + module.id() + ":" + module.index() + ".", throwable);
    }

    private void tickTimers() {
        long now = currentTick();
        List<GameTimer> dueTimers = new ArrayList<>();
        synchronized (tickerLock) {
            tickerTask = null;
            tickerWakeTick = Long.MAX_VALUE;
            while (!scheduledTimers.isEmpty()) {
                ScheduledTimer scheduled = scheduledTimers.peek();
                if (!scheduled.valid()) {
                    scheduledTimers.poll();
                    continue;
                }
                if (scheduled.wakeTick() > now) {
                    break;
                }
                scheduledTimers.poll();
                dueTimers.add(scheduled.timer());
            }
            scheduleNextTicker(now);
        }

        for (GameTimer timer : dueTimers) {
            timer.tick(now);
        }
    }

    private void stopTicker() {
        synchronized (tickerLock) {
            if (tickerTask != null) {
                tickerTask.cancel();
                tickerTask = null;
            }
            tickerWakeTick = Long.MAX_VALUE;
        }
    }

    private void scheduleNextTicker(long now) {
        while (!scheduledTimers.isEmpty() && !scheduledTimers.peek().valid()) {
            scheduledTimers.poll();
        }
        if (scheduledTimers.isEmpty()) {
            stopTicker();
            return;
        }

        long wakeTick = scheduledTimers.peek().wakeTick();
        if (tickerTask != null && tickerWakeTick <= wakeTick) {
            return;
        }
        if (tickerTask != null) {
            tickerTask.cancel();
        }

        tickerWakeTick = wakeTick;
        long delay = Math.max(1L, wakeTick - now);
        tickerTask = module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), this::tickTimers, delay);
    }

    private record ScheduledTimer(GameTimer timer, long wakeTick, long version) implements Comparable<ScheduledTimer> {
        private boolean valid() {
            return timer.isScheduled(wakeTick, version);
        }

        @Override
        public int compareTo(ScheduledTimer other) {
            int wakeComparison = Long.compare(wakeTick, other.wakeTick);
            if (wakeComparison != 0) {
                return wakeComparison;
            }
            return Long.compare(version, other.version);
        }
    }
}
