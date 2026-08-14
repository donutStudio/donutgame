package com.donutsforlife11.donutgame.api.ui.sidebar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import fr.mrmicky.fastboard.adventure.FastBoard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GameSidebar {
    private final GameModule module;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, FastBoard> boards = new LinkedHashMap<>();

    private Supplier<Collection<GamePlayer>> viewersSupplier;
    private Supplier<Component> titleSupplier;
    private GameTimer refreshTimer;

    public GameSidebar(GameModule module) {
        this.module = module;
        this.viewersSupplier = () -> List.copyOf(module.playerManager().getPlayers());
        this.titleSupplier = () -> Component.text(module.name(), NamedTextColor.GOLD, TextDecoration.BOLD).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    public GameSidebar viewers(Supplier<Collection<GamePlayer>> viewersSupplier) {
        return setViewers(viewersSupplier);
    }

    public GameSidebar setViewers(Collection<GamePlayer> viewers) {
        this.viewersSupplier = () -> List.copyOf(viewers);
        return this;
    }

    public GameSidebar setViewers(Supplier<Collection<GamePlayer>> viewersSupplier) {
        this.viewersSupplier = Objects.requireNonNull(viewersSupplier);
        return this;
    }

    public GameSidebar title(Supplier<Component> titleSupplier) {
        return setTitle(titleSupplier);
    }

    public GameSidebar setTitle(Component title) {
        this.titleSupplier = () -> title;
        return this;
    }

    public GameSidebar setTitle(Supplier<Component> titleSupplier) {
        this.titleSupplier = Objects.requireNonNull(titleSupplier);
        return this;
    }

    public GameSidebar blank() {
        return addLine();
    }

    public GameSidebar addLine() {
        entries.add(player -> new RenderedEntry(Component.empty(), Component.empty()));
        return this;
    }

    public GameSidebar addLine(Component component) {
        entries.add(player -> new RenderedEntry(component, Component.empty()));
        return this;
    }

    public GameSidebar integer(String label, IntSupplier valueSupplier) {
        return addInteger(label, valueSupplier);
    }

    public GameSidebar addInteger(String label, int value) {
        return addInteger(label, () -> value);
    }

    public GameSidebar addInteger(String label, IntSupplier valueSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(labelComponent(label), Component.text(valueSupplier.getAsInt(), NamedTextColor.AQUA).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))));
        return this;
    }

    public GameSidebar integer(String label, Function<GamePlayer, Integer> valueSupplier) {
        return addInteger(label, valueSupplier);
    }

    public GameSidebar addInteger(String label, Function<GamePlayer, Integer> valueSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(
            labelComponent(label).append(Component.text(valueSupplier.apply(player), NamedTextColor.AQUA).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128))),
            Component.empty()
        )));
        return this;
    }

    public GameSidebar fraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) {
        return addFraction(label, numeratorSupplier, denominatorSupplier);
    }

    public GameSidebar addFraction(String label, int numerator, int denominator) {
        return addFraction(label, () -> numerator, () -> denominator);
    }

    public GameSidebar addFraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(
            labelComponent(label),
            Component.text()
                .append(Component.text(numeratorSupplier.getAsInt(), NamedTextColor.YELLOW).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text("/", NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text(denominatorSupplier.getAsInt(), NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .build()
        )));
        return this;
    }

    public GameSidebar addFraction(String label, Function<GamePlayer, Integer> numeratorSupplier, Function<GamePlayer, Integer> denominatorSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(
            labelComponent(label),
            Component.text()
                .append(Component.text(numeratorSupplier.apply(player), NamedTextColor.YELLOW).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text("/", NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text(denominatorSupplier.apply(player), NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .build()
        )));
        return this;
    }

    public GameSidebar time(String label, IntSupplier secondsSupplier) {
        return addTime(label, secondsSupplier);
    }

    public GameSidebar addTime(String label, int ticks) {
        return addTime(label, () -> ticks);
    }

    public GameSidebar addTime(String label, IntSupplier ticksSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(labelComponent(label), formatTime(ticksSupplier.getAsInt() / 20))));
        return this;
    }

    public GameSidebar addTime(String label, Function<GamePlayer, Integer> ticksSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(labelComponent(label), formatTime(ticksSupplier.apply(player) / 20))));
        return this;
    }

    public GameSidebar timeSeconds(String label, IntSupplier secondsSupplier) {
        entries.add(new StaticEntry(label, player -> new RenderedEntry(labelComponent(label), formatTime(secondsSupplier.getAsInt()))));
        return this;
    }

    public GameSidebar dynamicTime(Supplier<String> labelSupplier, IntSupplier secondsSupplier) {
        entries.add(new DynamicLabelEntry(labelSupplier, player -> new RenderedEntry(labelComponent(labelSupplier.get()), formatTime(secondsSupplier.getAsInt()))));
        return this;
    }

    public GameSidebar setLabel(String label) {
        return setLabel(() -> label);
    }

    public GameSidebar setLabel(Supplier<String> labelSupplier) {
        if (entries.isEmpty()) return this;
        Entry previous = entries.removeLast();
        entries.add(new DynamicLabelEntry(labelSupplier, previous::render));
        return this;
    }

    public GameSidebar show() {
        if (refreshTimer != null) {
            return this;
        }
        refresh();
        refreshTimer = module.timeManager().newTimer().onTick(5, ignored -> refresh()).start();
        return this;
    }

    public GameSidebar hide() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        for (FastBoard board : boards.values()) {
            board.delete();
        }
        boards.clear();
        return this;
    }

    public GameSidebar removeLine(String label) {
        entries.removeIf(entry -> entry instanceof LabeledEntry labeled && labeled.label().equals(label));
        return this;
    }

    public void remove() {
        hide();
    }

    public void delete() {
        remove();
    }

    private void refresh() {
        Map<UUID, GamePlayer> viewers = new LinkedHashMap<>();
        for (GamePlayer viewer : viewersSupplier.get()) {
            if (viewer != null && viewer.player() != null) {
                viewers.put(viewer.uuid(), viewer);
            }
        }
        boards.entrySet().removeIf(entry -> {
            if (viewers.containsKey(entry.getKey())) {
                return false;
            }
            entry.getValue().delete();
            return true;
        });
        for (GamePlayer viewer : viewers.values()) {
            FastBoard board = boards.computeIfAbsent(viewer.uuid(), ignored -> new FastBoard(viewer.player()));
            board.updateTitle(titleSupplier.get());
            List<Component> lines = new ArrayList<>();
            List<Component> scores = new ArrayList<>();
            lines.add(Component.empty());
            scores.add(Component.empty());
            for (Entry entry : entries) {
                RenderedEntry rendered = entry.render(viewer);
                lines.add(rendered.line());
                scores.add(rendered.score());
            }
            board.updateLines(lines, scores);
        }
    }

    private Component labelComponent(String label) {
        return Component.text(label + ": ", NamedTextColor.WHITE).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    private Component formatTime(int seconds) {
        int clampedSeconds = Math.max(0, seconds);
        return Component.text(String.format("%02d:%02d", clampedSeconds / 60, clampedSeconds % 60), NamedTextColor.GREEN).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    private interface Entry {
        RenderedEntry render(GamePlayer player);
    }

    private interface LabeledEntry extends Entry {
        String label();
    }

    private record StaticEntry(String label, Entry delegate) implements LabeledEntry {
        @Override
        public RenderedEntry render(GamePlayer player) {
            return delegate.render(player);
        }
    }

    private record DynamicLabelEntry(Supplier<String> labelSupplier, Entry delegate) implements LabeledEntry {
        @Override
        public String label() {
            return labelSupplier.get();
        }

        @Override
        public RenderedEntry render(GamePlayer player) {
            return delegate.render(player);
        }
    }

    private record RenderedEntry(Component line, Component score) {
    }
}
