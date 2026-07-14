package com.donutsforlife11.donutgame.api.ui.sidebar;

import java.util.Collection;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.ui.UiManager;
import com.donutsforlife11.donutgame.internal.game.GameModule;

@SuppressWarnings("unused")
public class GameSidebar {
    private Supplier<Collection<Player>> viewersSupplier;
    private final GameModule module;
    private final UiManager uiManager;

    public GameSidebar(GameModule module, UiManager uiManager) {
        this.module = module;
        this.uiManager = uiManager;
        this.viewersSupplier = module.playerManager()::getPlayers;
    }
}

/* public static Component time(int ticks) {
        int seconds = ticks / 20;
        return Component.text(String.format("%02d:%02d", seconds / 60, seconds % 60), NamedTextColor.GREEN);
    }
 */