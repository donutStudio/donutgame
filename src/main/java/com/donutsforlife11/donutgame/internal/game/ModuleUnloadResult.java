package com.donutsforlife11.donutgame.internal.game;

public record ModuleUnloadResult(boolean unloaded, boolean hadErrors) {
    public static ModuleUnloadResult missing() {
        return new ModuleUnloadResult(false, false);
    }

    public static ModuleUnloadResult unloaded(boolean hadErrors) {
        return new ModuleUnloadResult(true, hadErrors);
    }
}
