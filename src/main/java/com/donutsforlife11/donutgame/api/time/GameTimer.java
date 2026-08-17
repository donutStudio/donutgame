package com.donutsforlife11.donutgame.api.time;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameTimer {
    private final TimeManager timeManager;

    private final Object stateLock = new Object();
    private final List<TickAction> onTickActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onFinishActions = new CopyOnWriteArrayList<>();

    private int maxTicks = -1;
    private volatile int elapsedTicks = 0;
    private volatile boolean started;
    private volatile boolean paused;
    private volatile boolean cancelled;
    private volatile boolean finished;

    public GameTimer(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    public GameTimer start() {
        synchronized (stateLock) {
            if (started) {
                throw new IllegalStateException("Timer already started!");
            }
            if (finished) {
                throw new IllegalStateException("Timer already finished!");
            }
            if (cancelled) {
                throw new IllegalStateException("Cancelled timers cannot be started again!");
            }
            started = true;
        }
        runTickActions();
        if (maxTicks == 0) {
            finishImmediately();
            return this;
        }
        timeManager.activate(this);
        return this;
    }

    public GameTimer setMaxTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("A timer cannot have negative ticks!");
        }
        maxTicks = ticks;
        return this;
    }

    public GameTimer setUnlimitedMaxTicks() {
        maxTicks = -1;
        return this;
    }

    public GameTimer pause() {
        setPaused(true);
        return this;
    }

    public GameTimer resume() {
        setPaused(false);
        return this;
    }

    public void cancel() {
        synchronized (stateLock) {
            if (cancelled || finished) {
                return;
            }
            cancelled = true;
            paused = true;
        }
        timeManager.deactivate(this);
    }

    public GameTimer onTick(Consumer<GameTimer> action) {
        return onTick(1, action);
    }

    public GameTimer onTick(int interval, Consumer<GameTimer> action) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Tick interval must be greater than zero.");
        }
        TickAction tickAction = new TickAction(interval, action);
        onTickActions.add(tickAction);
        if (started && !finished && !cancelled && elapsedTicks % interval == 0) {
            action.accept(this);
        }
        return this;
    }

    public GameTimer onFinish(Consumer<GameTimer> action) {
        onFinishActions.add(action);
        return this;
    }

    public boolean isStarted() {
        return started;
    }

    public int getMaxTicks() {
        return maxTicks;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public double getElapsedSeconds() {
        return elapsedTicks / 20.0;
    }

    public int getRemainingTicks() {
        if (maxTicks < 0) {
            return -1;
        }
        return Math.max(maxTicks - elapsedTicks, 0);
    }

    public double getRemainingSeconds() {
        int remaining = getRemainingTicks();
        return remaining < 0 ? -1 : remaining / 20.0;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isFinished() {
        return finished;
    }

    void tick() {
        if (!started || finished || cancelled) {
            return;
        }
        boolean ended = false;
        synchronized (stateLock) {
            if (!started || finished || cancelled) {
                return;
            }
            if (!paused) {
                elapsedTicks++;
                runTickActions();
            }
            if (maxTicks >= 0 && elapsedTicks >= maxTicks) {
                finished = true;
                paused = true;
                ended = true;
            }
        }
        if (ended) {
            timeManager.deactivate(this);
            for (Consumer<GameTimer> action : onFinishActions) {
                action.accept(this);
            }
        }
    }

    private void finishImmediately() {
        synchronized (stateLock) {
            if (finished || cancelled) {
                return;
            }
            finished = true;
            paused = true;
        }
        timeManager.deactivate(this);
        for (Consumer<GameTimer> action : onFinishActions) {
            action.accept(this);
        }
    }

    private void setPaused(boolean paused) {
        synchronized (stateLock) {
            if (finished || cancelled) {
                return;
            }
            this.paused = paused;
        }
    }

    private void runTickActions() {
        for (TickAction action : onTickActions) {
            if (elapsedTicks % action.interval() == 0) {
                action.action().accept(this);
            }
        }
    }

    private record TickAction(int interval, Consumer<GameTimer> action) {
    }
}
