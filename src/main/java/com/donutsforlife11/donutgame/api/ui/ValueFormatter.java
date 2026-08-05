package com.donutsforlife11.donutgame.api.ui;

import java.util.function.Consumer;

import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.api.time.TimeManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ValueFormatter {
    public static GameTimer countdown(TimeManager timeManager, int countdownTicks, Consumer<Component> action) {
        NamedTextColor[] countdownColors = 
            {NamedTextColor.DARK_RED, NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN};
        int totalSeconds = countdownTicks / 20;
        int baseBandLength = totalSeconds / countdownColors.length;
        int extraSeconds = totalSeconds % countdownColors.length;

        GameTimer countdownTimer = timeManager.newTimer(countdownTicks).onTick(20, countdown -> {
            int elapsedSeconds = Math.round(countdown.getElapsedTicks() / 20.0f);
            int colorIndex;
            if (elapsedSeconds < baseBandLength + extraSeconds) {
                colorIndex = 0;
            } else {
                colorIndex = 1 + ((elapsedSeconds - (baseBandLength+ extraSeconds)) / baseBandLength);
            }
            colorIndex = Math.min(colorIndex, countdownColors.length - 1);

            Component formattedNumber = Component.text(Math.round(countdown.getRemainingTicks() / 20.0f), countdownColors[colorIndex]).decorate(TextDecoration.BOLD);
            action.accept(formattedNumber);
        });

        return countdownTimer;
    }
}
