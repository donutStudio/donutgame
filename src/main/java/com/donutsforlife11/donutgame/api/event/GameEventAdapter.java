package com.donutsforlife11.donutgame.api.event;

import com.donutsforlife11.donutgame.internal.game.GameModule;

@FunctionalInterface
public interface GameEventAdapter<S, T> {
    T adapt(S source, GameModule module);
}
