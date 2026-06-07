package com.donutsforlife11.donutgame.api.ui;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public class ValueDisplay {
    public enum ValueType {
        INTEGER,
        DECIMAL,
        TIME,
        FRACTION,
        PERCENT,
        COMPONENT
    }

    public enum ValueScope {
        STATIC,
        GLOBAL,
        PLAYER
    }

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");

    private final ValueType type;
    private final ValueScope scope;
    private final String label;
    private final Function<Player, ResolvedValue> resolver;
    private final ToDoubleFunction<Player> numericResolver;

    private ValueDisplayRenderer renderer;

    ValueDisplay(
        ValueType type,
        ValueScope scope,
        String label,
        Function<Player, ResolvedValue> resolver,
        ToDoubleFunction<Player> numericResolver
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.label = Objects.requireNonNullElse(label, "");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.numericResolver = numericResolver;
    }

    public ValueType getType() {
        return type;
    }

    public ValueScope getScope() {
        return scope;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDynamic() {
        return scope != ValueScope.STATIC;
    }

    public boolean isGlobal() {
        return scope != ValueScope.PLAYER;
    }

    public ValueDisplay setRenderer(ValueDisplayRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        return this;
    }

    public ValueDisplay clearRenderer() {
        this.renderer = null;
        return this;
    }

    public Component getDisplay() {
        requireGlobalAccess("display");
        return render(ValueSurface.GENERIC, new UITheme(), null);
    }

    public Component getDisplay(Player player) {
        return render(ValueSurface.GENERIC, new UITheme(), requireViewer(player));
    }

    public String getValue() {
        requireGlobalAccess("value");
        return resolve(null).valueText();
    }

    public String getValue(Player player) {
        return resolve(requireViewer(player)).valueText();
    }

    int getNumericValue() {
        requireGlobalAccess("numeric value");
        return resolveNumeric(null);
    }

    int getNumericValue(Player player) {
        return resolveNumeric(requireViewer(player));
    }

    Component render(ValueSurface surface, UITheme theme, Player viewer) {
        ResolvedValue value = resolve(viewer);
        ValueDisplayParts parts = new ValueDisplayParts(label, Component.text(label), value.valueText(), value.valueComponent());
        ValueDisplayRenderer activeRenderer = renderer == null ? theme.getRenderer(surface) : renderer;
        return activeRenderer.render(new ValueDisplayContext(surface, type, scope, viewer), parts);
    }

    private ResolvedValue resolve(Player viewer) {
        return resolver.apply(requireViewerIfNeeded(viewer));
    }

    private int resolveNumeric(Player viewer) {
        if (numericResolver == null) {
            throw new IllegalStateException("ValueDisplay type " + type + " cannot be used as a numeric value.");
        }

        return (int) Math.round(numericResolver.applyAsDouble(requireViewerIfNeeded(viewer)));
    }

    private Player requireViewerIfNeeded(Player viewer) {
        if (scope == ValueScope.PLAYER && viewer == null) {
            throw new IllegalStateException("Player-scoped displays require a player.");
        }

        return viewer;
    }

    private Player requireViewer(Player player) {
        return Objects.requireNonNull(player, "player");
    }

    private void requireGlobalAccess(String target) {
        if (!isGlobal()) {
            throw new IllegalStateException("Player-scoped displays require a player to resolve " + target + ".");
        }
    }

    record ResolvedValue(String valueText, Component valueComponent) {
    }

    static ValueDisplay staticInteger(String label, int value) {
        return numberDisplay(ValueType.INTEGER, ValueScope.STATIC, label, player -> value, ValueDisplay::formatInteger);
    }

    static ValueDisplay globalInteger(String label, java.util.function.IntSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.INTEGER, ValueScope.GLOBAL, label, player -> supplier.getAsInt(), ValueDisplay::formatInteger);
    }

    static ValueDisplay playerInteger(String label, java.util.function.ToIntFunction<Player> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.INTEGER, ValueScope.PLAYER, label, supplier::applyAsInt, ValueDisplay::formatInteger);
    }

    static ValueDisplay staticDecimal(String label, double value) {
        return numberDisplay(ValueType.DECIMAL, ValueScope.STATIC, label, player -> value, ValueDisplay::formatDecimal);
    }

    static ValueDisplay globalDecimal(String label, java.util.function.DoubleSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.DECIMAL, ValueScope.GLOBAL, label, player -> supplier.getAsDouble(), ValueDisplay::formatDecimal);
    }

    static ValueDisplay playerDecimal(String label, java.util.function.ToDoubleFunction<Player> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.DECIMAL, ValueScope.PLAYER, label, supplier::applyAsDouble, ValueDisplay::formatDecimal);
    }

    static ValueDisplay staticTime(String label, int value) {
        return numberDisplay(ValueType.TIME, ValueScope.STATIC, label, player -> value, ValueDisplay::formatTime);
    }

    static ValueDisplay globalTime(String label, java.util.function.IntSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.TIME, ValueScope.GLOBAL, label, player -> supplier.getAsInt(), ValueDisplay::formatTime);
    }

    static ValueDisplay playerTime(String label, java.util.function.ToIntFunction<Player> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.TIME, ValueScope.PLAYER, label, supplier::applyAsInt, ValueDisplay::formatTime);
    }

    static ValueDisplay staticFraction(String label, int numerator, int denominator) {
        return fractionDisplay(ValueScope.STATIC, label, player -> numerator, player -> denominator);
    }

    static ValueDisplay globalFraction(
        String label,
        java.util.function.IntSupplier numerator,
        java.util.function.IntSupplier denominator
    ) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        return fractionDisplay(ValueScope.GLOBAL, label, player -> numerator.getAsInt(), player -> denominator.getAsInt());
    }

    static ValueDisplay playerFraction(
        String label,
        java.util.function.ToIntFunction<Player> numerator,
        java.util.function.ToIntFunction<Player> denominator
    ) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        return fractionDisplay(ValueScope.PLAYER, label, numerator::applyAsInt, denominator::applyAsInt);
    }

    static ValueDisplay staticPercent(String label, double value) {
        return numberDisplay(ValueType.PERCENT, ValueScope.STATIC, label, player -> value, ValueDisplay::formatPercent);
    }

    static ValueDisplay globalPercent(String label, java.util.function.DoubleSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.PERCENT, ValueScope.GLOBAL, label, player -> supplier.getAsDouble(), ValueDisplay::formatPercent);
    }

    static ValueDisplay playerPercent(String label, java.util.function.ToDoubleFunction<Player> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return numberDisplay(ValueType.PERCENT, ValueScope.PLAYER, label, supplier::applyAsDouble, ValueDisplay::formatPercent);
    }

    static ValueDisplay staticComponent(String label, Component value) {
        Objects.requireNonNull(value, "value");
        return componentDisplay(ValueScope.STATIC, label, player -> value);
    }

    static ValueDisplay globalComponent(String label, java.util.function.Supplier<Component> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return componentDisplay(ValueScope.GLOBAL, label, player -> supplier.get());
    }

    static ValueDisplay playerComponent(String label, java.util.function.Function<Player, Component> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return componentDisplay(ValueScope.PLAYER, label, supplier);
    }

    private static ValueDisplay numberDisplay(
        ValueType type,
        ValueScope scope,
        String label,
        ToDoubleFunction<Player> supplier,
        Function<Double, String> formatter
    ) {
        return new ValueDisplay(
            type,
            scope,
            label,
            player -> {
                double value = supplier.applyAsDouble(player);
                String text = formatter.apply(value);
                return new ResolvedValue(text, Component.text(text));
            },
            supplier
        );
    }

    private static ValueDisplay fractionDisplay(
        ValueScope scope,
        String label,
        java.util.function.ToIntFunction<Player> numerator,
        java.util.function.ToIntFunction<Player> denominator
    ) {
        return new ValueDisplay(
            ValueType.FRACTION,
            scope,
            label,
            player -> {
                int top = numerator.applyAsInt(player);
                int bottom = denominator.applyAsInt(player);
                String text = formatInteger(top) + "/" + formatInteger(bottom);
                return new ResolvedValue(text, Component.text(text));
            },
            numerator::applyAsInt
        );
    }

    private static ValueDisplay componentDisplay(ValueScope scope, String label, Function<Player, Component> supplier) {
        return new ValueDisplay(
            ValueType.COMPONENT,
            scope,
            label,
            player -> {
                Component value = Objects.requireNonNullElse(supplier.apply(player), Component.empty());
                return new ResolvedValue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(value), value);
            },
            null
        );
    }

    private static String formatInteger(double value) {
        return String.format(Locale.US, "%,d", Math.round(value));
    }

    private static String formatDecimal(double value) {
        synchronized (DECIMAL_FORMAT) {
            return DECIMAL_FORMAT.format(value);
        }
    }

    private static String formatTime(double seconds) {
        int totalSeconds = (int) Math.max(0, Math.round(seconds));
        int minutes = totalSeconds / 60;
        int remainder = totalSeconds % 60;
        return minutes + ":" + String.format(Locale.US, "%02d", remainder);
    }

    private static String formatPercent(double value) {
        return formatDecimal(value * 100.0) + "%";
    }
}
