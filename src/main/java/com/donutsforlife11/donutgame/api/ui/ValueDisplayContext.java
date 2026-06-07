package com.donutsforlife11.donutgame.api.ui;

import org.bukkit.entity.Player;

import com.donutsforlife11.donutgame.api.ui.ValueDisplay.ValueScope;
import com.donutsforlife11.donutgame.api.ui.ValueDisplay.ValueType;

public record ValueDisplayContext(
    ValueSurface surface,
    ValueType type,
    ValueScope scope,
    Player viewer
) {
}
