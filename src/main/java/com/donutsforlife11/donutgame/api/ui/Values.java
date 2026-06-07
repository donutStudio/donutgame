package com.donutsforlife11.donutgame.api.ui;

import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public final class Values {
    private Values() {
    }

    public static ValueDisplay integer(String label, int value) {
        return ValueDisplay.staticInteger(label, value);
    }

    public static ValueDisplay integer(String label, IntSupplier value) {
        return ValueDisplay.globalInteger(label, value);
    }

    public static ValueDisplay integer(String label, ToIntFunction<Player> value) {
        return ValueDisplay.playerInteger(label, value);
    }

    public static ValueDisplay decimal(String label, double value) {
        return ValueDisplay.staticDecimal(label, value);
    }

    public static ValueDisplay decimal(String label, DoubleSupplier value) {
        return ValueDisplay.globalDecimal(label, value);
    }

    public static ValueDisplay decimal(String label, ToDoubleFunction<Player> value) {
        return ValueDisplay.playerDecimal(label, value);
    }

    public static ValueDisplay time(String label, int time) {
        return ValueDisplay.staticTime(label, time);
    }

    public static ValueDisplay time(String label, IntSupplier time) {
        return ValueDisplay.globalTime(label, time);
    }

    public static ValueDisplay time(String label, ToIntFunction<Player> time) {
        return ValueDisplay.playerTime(label, time);
    }

    public static ValueDisplay fraction(String label, int numerator, int denominator) {
        return ValueDisplay.staticFraction(label, numerator, denominator);
    }

    public static ValueDisplay fraction(String label, IntSupplier numerator, IntSupplier denominator) {
        return ValueDisplay.globalFraction(label, numerator, denominator);
    }

    public static ValueDisplay fraction(String label, ToIntFunction<Player> numerator, ToIntFunction<Player> denominator) {
        return ValueDisplay.playerFraction(label, numerator, denominator);
    }

    public static ValueDisplay percent(String label, double percent) {
        return ValueDisplay.staticPercent(label, percent);
    }

    public static ValueDisplay percent(String label, DoubleSupplier percent) {
        return ValueDisplay.globalPercent(label, percent);
    }

    public static ValueDisplay percent(String label, ToDoubleFunction<Player> percent) {
        return ValueDisplay.playerPercent(label, percent);
    }

    public static ValueDisplay component(String label, Component value) {
        return ValueDisplay.staticComponent(label, value);
    }

    public static ValueDisplay component(String label, Supplier<Component> value) {
        return ValueDisplay.globalComponent(label, value);
    }

    public static ValueDisplay component(String label, Function<Player, Component> value) {
        return ValueDisplay.playerComponent(label, value);
    }
}
