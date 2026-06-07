package com.donutsforlife11.donutgame.api.ui;

import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface ValueDisplayRenderer {
    Component render(ValueDisplayContext context, ValueDisplayParts parts);
}
