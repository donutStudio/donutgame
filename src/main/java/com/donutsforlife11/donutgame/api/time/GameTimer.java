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
        }

        runTickActions(0);
        if (maxTicks() == 0) {
            finish();
            return this;
        }
        if (needsTicks()) {
            timeManager.startTicking();
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
            maxTicks = ticks;
            shouldFinish = started && maxTicks != UNLIMITED && elapsedTicks >= maxTicks;
        }
        if (shouldFinish) {
            finish();
        }
        return this;
    }

    public synchronized int maxTicks() {
        return maxTicks;
    }

    public synchronized GameTimer pause() {
        if (!finished && !cancelled) {
            paused = true;
        }
        return this;
    }

    public synchronized GameTimer resume() {
        if (!finished && !cancelled) {
            paused = false;
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
            cancelled = true;
            paused = true;
        }
        timeManager.remove(this);
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized int elapsedTicks() {
        return elapsedTicks;
    }

    public synchronized double elapsedSeconds() {
        return elapsedTicks / 20.0;
    }

    public synchronized int remainingTicks() {
        if (maxTicks == UNLIMITED) {
            return UNLIMITED;
        }
        return Math.max(maxTicks - elapsedTicks, 0);
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
            runNow = started && !finished && !cancelled && elapsedTicks % interval == 0;
        }
        if (runNow) {
            runAction(action);
        }
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
        return started && !finished && !cancelled;
    }

    void tick() {
        int currentTicks;
        boolean shouldFinish;
        synchronized (this) {
            if (!needsTicks() || paused) {
                return;
            }
            elapsedTicks++;
            currentTicks = elapsedTicks;
            shouldFinish = maxTicks != UNLIMITED && elapsedTicks >= maxTicks;
        }

        runTickActions(currentTicks);
        if (shouldFinish) {
            finish();
        }
    }

    private void finish() {
        List<Consumer<GameTimer>> actions;
        synchronized (this) {
            if (finished || cancelled) {
                return;
            }
            finished = true;
            paused = true;
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

    private record TickAction(int interval, Consumer<GameTimer> action) {
    }
}
