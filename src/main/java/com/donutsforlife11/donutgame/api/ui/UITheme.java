package com.donutsforlife11.donutgame.api.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class UITheme {
    private final Map<ValueSurface, ValueDisplayRenderer> renderers = new EnumMap<>(ValueSurface.class);

    public UITheme() {
        renderers.put(ValueSurface.GENERIC, UITheme::renderGeneric);
        renderers.put(ValueSurface.SIDEBAR, UITheme::renderSidebar);
        renderers.put(ValueSurface.BOSSBAR, UITheme::renderBossbar);
    }

    public ValueDisplayRenderer getRenderer(ValueSurface surface) {
        return renderers.get(surface);
    }

    public UITheme setRenderer(ValueSurface surface, ValueDisplayRenderer renderer) {
        renderers.put(Objects.requireNonNull(surface, "surface"), Objects.requireNonNull(renderer, "renderer"));
        return this;
    }

    private static Component renderGeneric(ValueDisplayContext context, ValueDisplayParts parts) {
        if (!parts.hasLabel()) {
            return parts.valueComponent().colorIfAbsent(NamedTextColor.WHITE);
        }

        return parts.labelComponent().colorIfAbsent(NamedTextColor.GRAY)
            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
            .append(parts.valueComponent().colorIfAbsent(NamedTextColor.WHITE));
    }

    private static Component renderSidebar(ValueDisplayContext context, ValueDisplayParts parts) {
        if (!parts.hasLabel()) {
            return parts.valueComponent().colorIfAbsent(NamedTextColor.WHITE);
        }

        return parts.labelComponent().colorIfAbsent(NamedTextColor.GRAY)
            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
            .append(parts.valueComponent().colorIfAbsent(NamedTextColor.WHITE));
    }

    private static Component renderBossbar(ValueDisplayContext context, ValueDisplayParts parts) {
        if (!parts.hasLabel()) {
            return parts.valueComponent().colorIfAbsent(NamedTextColor.WHITE);
        }

        return parts.labelComponent().colorIfAbsent(NamedTextColor.GOLD)
            .append(Component.text(" ", NamedTextColor.DARK_GRAY))
            .append(parts.valueComponent().colorIfAbsent(NamedTextColor.YELLOW));
    }
}
