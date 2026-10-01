package com.donutsforlife11.donutgame.api.time;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameTimer {
    public static final int UNLIMITED = -1;

    private final TimeManager timeManager;
    private final List<TickAction> tickActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> finishActions = new CopyOnWriteArrayList<>();

    private int maxTicks = UNLIMITED;
    private int elapsedTicks;
    private long lastResumeTick;
    private long scheduledWakeTick = Long.MAX_VALUE;
    private long scheduleVersion;
    private boolean started;
    private boolean paused;
    private boolean cancelled;
    private boolean finished;

    GameTimer(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    public GameTimer start() {
        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Timer has already been started.");
            }
            if (cancelled) {
                throw new IllegalStateException("Cancelled timers cannot be started.");
            }
            if (finished) {
                throw new IllegalStateException("Finished timers cannot be started.");
            }
            started = true;
            lastResumeTick = timeManager.currentTick();
        }

        timeManager.register(this);
        runTickActions(0);
        if (maxTicks() == 0) {
            finish();
            return this;
        }
        return this;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public GameTimer setMaxTicks(int ticks) {
        if (ticks < UNLIMITED) {
            throw new IllegalArgumentException("Timer max ticks cannot be less than GameTimer.UNLIMITED.");
        }
        boolean shouldFinish;
        synchronized (this) {
            updateElapsedTicks(timeManager.currentTick());
            maxTicks = ticks;
            shouldFinish = started && maxTicks != UNLIMITED && elapsedTicks >= maxTicks;
        }
        if (shouldFinish) {
            finish();
        } else {
            timeManager.schedule(this);
        }
        return this;
    }

    public synchronized int maxTicks() {
        return maxTicks;
    }

    public GameTimer pause() {
        boolean changed = false;
        synchronized (this) {
            if (!finished && !cancelled) {
                updateElapsedTicks(timeManager.currentTick());
                paused = true;
                clearSchedule();
                changed = true;
            }
        }
        if (changed) {
            timeManager.schedule(this);
        }
        return this;
    }

    public GameTimer resume() {
        boolean shouldStartTicking;
        synchronized (this) {
            if (!finished && !cancelled) {
                if (paused) {
                    lastResumeTick = timeManager.currentTick();
                }
                paused = false;
            }
            shouldStartTicking = needsTicks();
        }
        if (shouldStartTicking) {
            timeManager.schedule(this);
        }
        return this;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public void cancel() {
        synchronized (this) {
            if (cancelled || finished) {
                return;
            }
            updateElapsedTicks(timeManager.currentTick());
            cancelled = true;
            paused = true;
            clearSchedule();
        }
        timeManager.remove(this);
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public int elapsedTicks() {
        synchronized (this) {
            return currentElapsedTicks(timeManager.currentTick());
        }
    }

    public double elapsedSeconds() {
        return elapsedTicks() / 20.0;
    }

    public int remainingTicks() {
        synchronized (this) {
            if (maxTicks == UNLIMITED) {
                return UNLIMITED;
            }
            return Math.max(maxTicks - currentElapsedTicks(timeManager.currentTick()), 0);
        }
    }

    public synchronized double remainingSeconds() {
        int remainingTicks = remainingTicks();
        if (remainingTicks == UNLIMITED) {
            return UNLIMITED;
        }
        return remainingTicks / 20.0;
    }

    public GameTimer onTick(Consumer<GameTimer> action) {
        return onTick(1, action);
    }

    public GameTimer onTick(int interval, Consumer<GameTimer> action) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Tick interval must be greater than 0.");
        }
        if (action == null) {
            throw new IllegalArgumentException("Tick action cannot be null.");
        }
        tickActions.add(new TickAction(interval, action));
        boolean runNow;
        synchronized (this) {
            int currentTicks = currentElapsedTicks(timeManager.currentTick());
            runNow = started && !paused && !finished && !cancelled && currentTicks % interval == 0;
        }
        if (runNow) {
            runAction(action);
        }
        timeManager.schedule(this);
        return this;
    }

    public GameTimer onFinish(Consumer<GameTimer> action) {
        if (action == null) {
            throw new IllegalArgumentException("Finish action cannot be null.");
        }
        boolean runNow;
        synchronized (this) {
            runNow = finished;
            if (!runNow) {
                finishActions.add(action);
            }
        }
        if (runNow) {
            runAction(action);
        }
        return this;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    synchronized boolean needsTicks() {
        return started && !paused && !finished && !cancelled;
    }

    long nextWakeTick(long now) {
        synchronized (this) {
            if (!needsTicks()) {
                return Long.MAX_VALUE;
            }

            int currentTicks = currentElapsedTicks(now);
            long nextElapsed = Long.MAX_VALUE;
            for (TickAction action : tickActions) {
                long interval = action.interval();
                long candidate = ((currentTicks / interval) + 1L) * interval;
                nextElapsed = Math.min(nextElapsed, candidate);
            }
            if (maxTicks != UNLIMITED && maxTicks > currentTicks) {
                nextElapsed = Math.min(nextElapsed, maxTicks);
            }
            if (nextElapsed == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return now + Math.max(1L, nextElapsed - currentTicks);
        }
    }

    synchronized long assignSchedule(long wakeTick) {
        scheduledWakeTick = wakeTick;
        return ++scheduleVersion;
    }

    synchronized void clearSchedule() {
        scheduledWakeTick = Long.MAX_VALUE;
        scheduleVersion++;
    }

    synchronized boolean isScheduled(long wakeTick, long version) {
        return scheduledWakeTick == wakeTick && scheduleVersion == version && needsTicks();
    }

    void tick(long now) {
        int currentTicks;
        boolean shouldFinish;
        synchronized (this) {
            if (!needsTicks() || paused) {
                return;
            }
            updateElapsedTicks(now);
            currentTicks = elapsedTicks;
            shouldFinish = maxTicks != UNLIMITED && elapsedTicks >= maxTicks;
        }

        runTickActions(currentTicks);
        if (shouldFinish) {
            finish();
        } else {
            timeManager.schedule(this);
        }
    }

    private void finish() {
        List<Consumer<GameTimer>> actions;
        synchronized (this) {
            if (finished || cancelled) {
                return;
            }
            updateElapsedTicks(timeManager.currentTick());
            finished = true;
            paused = true;
            clearSchedule();
            actions = List.copyOf(finishActions);
        }

        timeManager.remove(this);
        for (Consumer<GameTimer> action : actions) {
            runAction(action);
        }
    }

    private void runTickActions(int currentTicks) {
        for (TickAction action : tickActions) {
            if (currentTicks % action.interval() == 0) {
                runAction(action.action());
            }
        }
    }

    private void runAction(Consumer<GameTimer> action) {
        try {
            action.accept(this);
        } catch (Throwable throwable) {
            timeManager.logTimerException(throwable);
        }
    }

    private int currentElapsedTicks(long now) {
        if (!started || paused || cancelled || finished) {
            return elapsedTicks;
        }
        long total = (long) elapsedTicks + Math.max(0L, now - lastResumeTick);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private void updateElapsedTicks(long now) {
        elapsedTicks = currentElapsedTicks(now);
        lastResumeTick = now;
    }

    private record TickAction(int interval, Consumer<GameTimer> action) {
    }
}
