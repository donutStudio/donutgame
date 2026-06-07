package com.donutsforlife11.donutgame.api.ui;

import net.kyori.adventure.text.Component;

public record ValueDisplayParts(
    String labelText,
    Component labelComponent,
    String valueText,
    Component valueComponent
) {
    public boolean hasLabel() {
        return !labelText.isBlank();
    }
}
