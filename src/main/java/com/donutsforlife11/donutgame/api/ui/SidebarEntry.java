package com.donutsforlife11.donutgame.api.ui;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;

public class SidebarEntry {
    private Function<GamePlayer, String> label;
    private String fallbackLabel;
    private final boolean playerSpecific;
    private Function<GamePlayer, RenderedEntry> renderer;

    private SidebarEntry(Function<GamePlayer, String> label, boolean playerSpecific, Function<GamePlayer, RenderedEntry> renderer) {
        this.label = label;
        this.playerSpecific = playerSpecific;
        this.renderer = Objects.requireNonNull(renderer);
    }

    public static SidebarEntry blank() {
        return custom(Component.empty());
    }

    public static SidebarEntry custom(Component component) {
        return custom(() -> component);
    }

    public static SidebarEntry custom(Supplier<Component> component) {
        Objects.requireNonNull(component);
        return new SidebarEntry(null, false, player -> new RenderedEntry(component.get(), Component.empty()));
    }

    public static SidebarEntry custom(Function<GamePlayer, Component> component) {
        Objects.requireNonNull(component);
        return new SidebarEntry(null, true, player -> new RenderedEntry(component.apply(player), Component.empty()));
    }

    public static SidebarEntry integer(String label, int value) {
        return integer(label, () -> value);
    }

    public static SidebarEntry integer(String label, IntSupplier value) {
        Objects.requireNonNull(value);
        return labeled(label, false, player -> Component.text(value.getAsInt(), NamedTextColor.YELLOW));
    }

    public static SidebarEntry integer(String label, Function<GamePlayer, Integer> value) {
        Objects.requireNonNull(value);
        return labeled(label, true, player -> Component.text(value.apply(player), NamedTextColor.AQUA));
    }

    public static SidebarEntry fraction(String label, int numerator, int denominator) {
        return fraction(label, () -> numerator, () -> denominator);
    }

    public static SidebarEntry fraction(String label, IntSupplier numerator, IntSupplier denominator) {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(denominator);
        return labeled(label, false, player -> Component.text()
            .append(Component.text(numerator.getAsInt(), NamedTextColor.YELLOW))
            .append(Component.text("/" + denominator.getAsInt(), NamedTextColor.GRAY))
            .build());
    }

    public static SidebarEntry fraction(String label, Function<GamePlayer, Integer> numerator, Function<GamePlayer, Integer> denominator) {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(denominator);
        return labeled(label, true, player -> Component.text()
            .append(Component.text(numerator.apply(player), NamedTextColor.AQUA))
            .append(Component.text("/" + denominator.apply(player), NamedTextColor.GRAY))
            .build());
    }

    public static SidebarEntry time(String label, int ticks) {
        return time(label, () -> ticks);
    }

    public static SidebarEntry time(String label, IntSupplier ticks) {
        Objects.requireNonNull(ticks);
        return labeled(label, false, player -> Component.text(formatTime(ticks.getAsInt()), NamedTextColor.GREEN));
    }

    public static SidebarEntry time(String label, Function<GamePlayer, Integer> ticks) {
        Objects.requireNonNull(ticks);
        return labeled(label, true, player -> Component.text(formatTime(ticks.apply(player)), NamedTextColor.LIGHT_PURPLE));
    }

    public static SidebarEntry component(String label, Component component) {
        return component(label, () -> component);
    }

    public static SidebarEntry component(String label, Supplier<Component> component) {
        Objects.requireNonNull(component);
        return labeled(label, false, player -> component.get());
    }

    public static SidebarEntry component(String label, Function<GamePlayer, Component> component) {
        Objects.requireNonNull(component);
        return labeled(label, true, component);
    }

    public SidebarEntry setLabel(String label) {
        return setLabel(() -> label);
    }

    public SidebarEntry setLabel(Supplier<String> label) {
        Objects.requireNonNull(label);
        this.label = player -> {
            fallbackLabel = label.get();
            return fallbackLabel;
        };
        return this;
    }

    public SidebarEntry setLabel(Function<GamePlayer, String> label) {
        fallbackLabel = null;
        this.label = Objects.requireNonNull(label);
        return this;
    }

    public SidebarEntry removeLabel() {
        this.label = null;
        this.fallbackLabel = null;
        return this;
    }

    public String label() {
        return fallbackLabel;
    }

    RenderedEntry render(GamePlayer player) {
        RenderedEntry rendered = renderer.apply(player);
        if (label == null) {
            return rendered;
        }

        Component score = rendered.score();
        if (score == null || Component.empty().equals(score)) {
            score = rendered.line();
        }
        String renderedLabelText = label.apply(player);
        Component renderedLabel = Component.text(renderedLabelText == null || renderedLabelText.isBlank() ? "" : renderedLabelText + ": ", NamedTextColor.WHITE)
            .shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
        if (playerSpecific) {
            return new RenderedEntry(Component.empty().append(renderedLabel).append(safe(score)), Component.empty());
        }
        return new RenderedEntry(renderedLabel, safe(score));
    }

    private static SidebarEntry labeled(String label, boolean playerSpecific, Function<GamePlayer, Component> value) {
        SidebarEntry entry = new SidebarEntry(null, playerSpecific, player -> new RenderedEntry(Component.empty(), value.apply(player)));
        return entry.setLabel(label);
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    static Component safe(Component component) {
        return component == null ? Component.empty() : shadow(component);
    }

    private static Component shadow(Component component) {
        return component == null ? Component.empty() : component.shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    record RenderedEntry(Component line, Component score) {
    }
}
