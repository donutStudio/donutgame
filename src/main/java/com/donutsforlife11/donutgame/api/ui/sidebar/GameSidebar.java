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
        this.titleSupplier = () -> shadow(Component.text(module.name(), NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    public GameSidebar setViewers(Collection<GamePlayer> viewers) {
        viewersSupplier = () -> viewers == null ? List.of() : List.copyOf(viewers);
        return this;
    }

    public GameSidebar setViewers(Supplier<Collection<GamePlayer>> viewersSupplier) {
        this.viewersSupplier = Objects.requireNonNull(viewersSupplier);
        return this;
    }

    public GameSidebar setTitle(Component title) {
        titleSupplier = () -> title;
        return this;
    }

    public GameSidebar setTitle(Supplier<Component> titleSupplier) {
        this.titleSupplier = Objects.requireNonNull(titleSupplier);
        return this;
    }

    public GameSidebar addLine() {
        entries.add(player -> new RenderedEntry(Component.empty(), Component.empty()));
        return this;
    }

    public GameSidebar addLine(Component component) {
        entries.add(player -> new RenderedEntry(component, Component.empty()));
        return this;
    }

    public GameSidebar addInteger(String label, int value) {
        return addInteger(label, () -> value);
    }

    public GameSidebar addInteger(String label, IntSupplier valueSupplier) {
        entries.add(new LabeledValueEntry(label, () -> label, player -> Component.text(valueSupplier.getAsInt(), NamedTextColor.YELLOW)));
        return this;
    }

    public GameSidebar addInteger(String label, Function<GamePlayer, Integer> valueSupplier) {
        entries.add(new LabeledLineEntry(label, () -> label, (player, labelSupplier) ->
            new RenderedEntry(label(labelSupplier.get()).append(Component.text(valueSupplier.apply(player), NamedTextColor.AQUA)), Component.empty())
        ));
        return this;
    }

    public GameSidebar addFraction(String label, int numerator, int denominator) {
        return addFraction(label, () -> numerator, () -> denominator);
    }

    public GameSidebar addFraction(String label, IntSupplier numeratorSupplier, int denominator) {
        return addFraction(label, numeratorSupplier, () -> denominator);
    }

    public GameSidebar addFraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) {
        entries.add(new LabeledValueEntry(label, () -> label, player -> Component.text()
            .append(Component.text(numeratorSupplier.getAsInt(), NamedTextColor.YELLOW))
            .append(Component.text("/" + denominatorSupplier.getAsInt(), NamedTextColor.GRAY))
            .build()
        ));
        return this;
    }

    public GameSidebar addFraction(String label, Function<GamePlayer, Integer> numeratorSupplier, Function<GamePlayer, Integer> denominatorSupplier) {
        entries.add(new LabeledLineEntry(label, () -> label, (player, labelSupplier) ->
            new RenderedEntry(label(labelSupplier.get()).append(Component.text()
            .append(Component.text(numeratorSupplier.apply(player), NamedTextColor.AQUA))
            .append(Component.text("/" + denominatorSupplier.apply(player), NamedTextColor.GRAY))
            .build()
        ), Component.empty())));
        return this;
    }

    public GameSidebar addTime(String label, int ticks) {
        return addTime(label, () -> ticks);
    }

    public GameSidebar addTime(String label, IntSupplier ticksSupplier) {
        entries.add(new LabeledValueEntry(label, () -> label, player -> Component.text(formatTime(ticksSupplier.getAsInt()), NamedTextColor.GREEN)));
        return this;
    }

    public GameSidebar addTime(String label, Function<GamePlayer, Integer> ticksSupplier) {
        entries.add(new LabeledLineEntry(label, () -> label, (player, labelSupplier) ->
            new RenderedEntry(label(labelSupplier.get()).append(Component.text(formatTime(ticksSupplier.apply(player)), NamedTextColor.LIGHT_PURPLE)), Component.empty())
        ));
        return this;
    }

    public GameSidebar setLabel(String label, String newLabel) {
        return setLabel(label, () -> newLabel);
    }

    public GameSidebar setLabel(String label, Supplier<String> newLabelSupplier) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry instanceof LabeledEntry labeled && labeled.label().equals(label)) {
                entries.set(i, labeled.withLabel(newLabelSupplier));
                return this;
            }
        }
        return this;
    }

    public GameSidebar removeLine(String label) {
        entries.removeIf(entry -> entry instanceof LabeledEntry labeled && labeled.label().equals(label));
        return this;
    }

    public GameSidebar show() {
        if (refreshTimer != null) return this;
        refresh();
        refreshTimer = module.timeManager().newModuleTimer().onTick(5, ignored -> refresh()).start();
        return this;
    }

    public GameSidebar hide() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        for (FastBoard board : boards.values()) board.delete();
        boards.clear();
        return this;
    }

    public void remove() {
        hide();
    }

    private void refresh() {
        Map<UUID, GamePlayer> viewers = new LinkedHashMap<>();
        Collection<GamePlayer> supplied = viewersSupplier.get();
        if (supplied != null) {
            for (GamePlayer viewer : supplied) {
                if (viewer != null && viewer.player() != null) viewers.put(viewer.uuid(), viewer);
            }
        }
        boards.entrySet().removeIf(entry -> {
            if (viewers.containsKey(entry.getKey())) return false;
            entry.getValue().delete();
            return true;
        });
        for (GamePlayer viewer : viewers.values()) {
            FastBoard board = boards.computeIfAbsent(viewer.uuid(), ignored -> new FastBoard(viewer.player()));
            board.updateTitle(titleSupplier.get() == null ? Component.empty() : titleSupplier.get());
            List<Component> lines = new ArrayList<>();
            List<Component> scores = new ArrayList<>();
            lines.add(Component.empty());
            scores.add(Component.empty());
            for (Entry entry : entries) {
                RenderedEntry rendered = entry.render(viewer);
                lines.add(rendered.line() == null ? Component.empty() : rendered.line());
                scores.add(rendered.score() == null ? Component.empty() : rendered.score());
            }
            lines.add(Component.empty());
            scores.add(Component.empty());
            board.updateLines(lines, scores);
        }
    }

    private Component label(String label) {
        return shadow(Component.text(label + ": ", NamedTextColor.WHITE));
    }

    private String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static Component shadow(Component component) {
        return component == null ? Component.empty() : component.shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    private interface Entry {
        RenderedEntry render(GamePlayer player);
    }

    private interface LabeledEntry extends Entry {
        String label();
        LabeledEntry withLabel(Supplier<String> labelSupplier);
    }

    private record LabeledValueEntry(String label, Supplier<String> labelSupplier, Function<GamePlayer, Component> valueSupplier) implements LabeledEntry {
        @Override
        public LabeledEntry withLabel(Supplier<String> labelSupplier) {
            return new LabeledValueEntry(label, Objects.requireNonNull(labelSupplier), valueSupplier);
        }

        @Override
        public RenderedEntry render(GamePlayer player) {
            return new RenderedEntry(shadow(Component.text(labelSupplier.get() + ": ", NamedTextColor.WHITE)), shadow(valueSupplier.apply(player)));
        }
    }

    private record LabeledLineEntry(String label, Supplier<String> labelSupplier, LabeledRenderer renderer) implements LabeledEntry {
        @Override
        public LabeledEntry withLabel(Supplier<String> labelSupplier) {
            return new LabeledLineEntry(label, Objects.requireNonNull(labelSupplier), renderer);
        }

        @Override
        public RenderedEntry render(GamePlayer player) {
            return renderer.render(player, labelSupplier);
        }
    }

    private interface LabeledRenderer {
        RenderedEntry render(GamePlayer player, Supplier<String> labelSupplier);
    }

    private record RenderedEntry(Component line, Component score) {
    }
}
