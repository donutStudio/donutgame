package com.donutsforlife11.donutgame.api.time;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameTimer {
    private final TimeManager manager;
    private final int targetTicks;

    private final Object stateLock = new Object();

    private final List<TimerAction> whileRunningActions = new CopyOnWriteArrayList<>();
    private final List<TimerAction> whilePausedActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onEndActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onCancelActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onToggleActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onRunningActions = new CopyOnWriteArrayList<>();
    private final List<Consumer<GameTimer>> onPausedActions = new CopyOnWriteArrayList<>();

    private volatile boolean started;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile boolean cancelled;
    private volatile int elapsedTicks;
    private volatile int runningTicks;
    private volatile int pausedTicks;

    GameTimer(TimeManager manager, int targetTicks) {
        this.manager = manager;
        this.targetTicks = targetTicks;

        if (targetTicks < -1) {
            throw new IllegalArgumentException("Timer target cannot be less than -1.");
        }
    }

    public GameTimer start() {
        boolean endedImmediately = false;

        synchronized (stateLock) {
            if (started) {
                throw new IllegalStateException("Timer has already been started.");
            }
            if (finished) {
                throw new IllegalStateException("Finished timers cannot be started again.");
            }
            if (cancelled) {
                throw new IllegalStateException("Cancelled timers cannot be started again.");
            }

            started = true;
            if (hasReachedTarget()) {
                finished = true;
                running = false;
                endedImmediately = true;
            } else {
                manager.activate(this);
            }
        }

        if (endedImmediately) {
            runCallbacks(onEndActions, "onEnd");
        }

        return this;
    }

    public boolean isStarted() {
        return started;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public int getElapsedSeconds() {
        return Math.round(elapsedTicks / 20.0f);
    }

    public int getRemainingTicks() {
        if (targetTicks < 0) {
            return -1;
        }

        return Math.max(targetTicks - elapsedTicks, 0);
    }

    public int getRemainingSeconds() {
        int remainingTicks = getRemainingTicks();

        if (remainingTicks < 0) {
            return -1;
        }

        return Math.round(remainingTicks / 20.0f);
    }

    public boolean isFinished() {
        return finished;
    }

    public GameTimer toggleRunning() {
        synchronized (stateLock) {
            if (!started || finished || cancelled) {
                return this;
            }

            setRunningState(!running);
        }

        return this;
    }

    public GameTimer setRunning(boolean running) {
        synchronized (stateLock) {
            if (finished || cancelled) {
                return this;
            }

            if (!started) {
                this.running = running;
                return this;
            }

            setRunningState(running);
        }

        return this;
    }

    public GameTimer pause() {
        return setRunning(false);
    }

    public GameTimer resume() {
        return setRunning(true);
    }

    public boolean isRunning() {
        return started && running && !finished && !cancelled;
    }

    public GameTimer cancel() {
        cancelInternal(true);
        return this;
    }

    void cancelFromManager() {
        cancelInternal(false);
    }

    public GameTimer whileRunning(Consumer<GameTimer> action) {
        return whileRunning(1, action);
    }

    public GameTimer whileRunning(int interval, Consumer<GameTimer> action) {
        whileRunningActions.add(new TimerAction(interval, action));
        return this;
    }

    public GameTimer onEnd(Consumer<GameTimer> action) {
        onEndActions.add(requireAction(action));
        return this;
    }

    public GameTimer onCancel(Consumer<GameTimer> action) {
        onCancelActions.add(requireAction(action));
        return this;
    }

    public GameTimer onToggleRunning(Consumer<GameTimer> action) {
        onToggleActions.add(requireAction(action));
        return this;
    }

    public GameTimer onToggleRunning(boolean running, Consumer<GameTimer> action) {
        if (running) {
            onRunningActions.add(requireAction(action));
        } else {
            onPausedActions.add(requireAction(action));
        }

        return this;
    }

    public GameTimer whilePaused(Consumer<GameTimer> action) {
        return whilePaused(1, action);
    }

    public GameTimer whilePaused(int interval, Consumer<GameTimer> action) {
        whilePausedActions.add(new TimerAction(interval, action));
        return this;
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

            if (running) {
                elapsedTicks++;
                runningTicks++;
            } else {
                pausedTicks++;
            }

            if (running) {
                runActions(whileRunningActions, runningTicks);
            } else {
                runActions(whilePausedActions, pausedTicks);
            }

            if (hasReachedTarget()) {
                finished = true;
                running = false;
                ended = true;
            }
        }

        if (ended) {
            manager.deactivate(this);
            runCallbacks(onEndActions, "onEnd");
        }
    }

    private void cancelInternal(boolean runCallbacks) {
        boolean cancelledNow = false;

        synchronized (stateLock) {
            if (finished || cancelled) {
                return;
            }

            cancelled = true;
            running = false;
            cancelledNow = started;
        }

        manager.deactivate(this);

        if (runCallbacks && cancelledNow) {
            runCallbacks(onCancelActions, "onCancel");
        }
    }

    private boolean hasReachedTarget() {
        if (targetTicks < 0) {
            return false;
        }

        return elapsedTicks >= targetTicks;
    }

    private void setRunningState(boolean running) {
        if (this.running == running) {
            return;
        }

        this.running = running;
        runCallbacks(onToggleActions, "onToggleRunning");

        if (running) {
            runCallbacks(onRunningActions, "onToggleRunning(true)");
        } else {
            runCallbacks(onPausedActions, "onToggleRunning(false)");
        }
    }

    private void runActions(List<TimerAction> actions, int phaseTicks) {
        for (TimerAction action : actions) {
            if (phaseTicks % action.interval() == 0) {
                runCallback(action.action(), action.label());
            }
        }
    }

    private void runCallbacks(List<Consumer<GameTimer>> callbacks, String callbackType) {
        for (Consumer<GameTimer> callback : callbacks) {
            runCallback(callback, callbackType);
        }
    }

    private void runCallback(Consumer<GameTimer> callback, String callbackType) {
        try {
            callback.accept(this);
        } catch (Throwable throwable) {
            manager.logCallbackFailure(callbackType, throwable);
        }
    }

    private Consumer<GameTimer> requireAction(Consumer<GameTimer> action) {
        return Objects.requireNonNull(action, "action");
    }

    private record TimerAction(int interval, Consumer<GameTimer> action) {
        private TimerAction {
            Objects.requireNonNull(action, "action");

            if (interval < 1) {
                throw new IllegalArgumentException("Timer callback interval must be at least 1 tick.");
            }
        }

        private String label() {
            return interval == 1 ? "whileRunning/whilePaused" : "interval callback";
        }
    }
}
