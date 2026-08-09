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
        this.viewersSupplier = Objects.requireNonNull(viewersSupplier);
        return this;
    }

    public GameSidebar title(Supplier<Component> titleSupplier) {
        this.titleSupplier = Objects.requireNonNull(titleSupplier);
        return this;
    }

    public GameSidebar blank() {
        entries.add(player -> new RenderedEntry(Component.empty(), Component.empty()));
        return this;
    }

    public GameSidebar integer(String label, IntSupplier valueSupplier) {
        entries.add(player -> new RenderedEntry(labelComponent(label), Component.text(valueSupplier.getAsInt(), NamedTextColor.AQUA).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128))));
        return this;
    }

    public GameSidebar integer(String label, Function<GamePlayer, Integer> valueSupplier) {
        entries.add(player -> new RenderedEntry(
            labelComponent(label).append(Component.text(valueSupplier.apply(player), NamedTextColor.AQUA).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128))),
            Component.empty()
        ));
        return this;
    }

    public GameSidebar fraction(String label, IntSupplier numeratorSupplier, IntSupplier denominatorSupplier) {
        entries.add(player -> new RenderedEntry(
            labelComponent(label),
            Component.text()
                .append(Component.text(numeratorSupplier.getAsInt(), NamedTextColor.YELLOW).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text("/", NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .append(Component.text(denominatorSupplier.getAsInt(), NamedTextColor.GRAY).shadowColor(ShadowColor.shadowColor(0, 0, 0, 128)))
                .build()
        ));
        return this;
    }

    public GameSidebar time(String label, IntSupplier secondsSupplier) {
        entries.add(player -> new RenderedEntry(labelComponent(label), formatTime(secondsSupplier.getAsInt())));
        return this;
    }

    public GameSidebar dynamicTime(Supplier<String> labelSupplier, IntSupplier secondsSupplier) {
        entries.add(player -> new RenderedEntry(labelComponent(labelSupplier.get()), formatTime(secondsSupplier.getAsInt())));
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

    public void delete() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        for (FastBoard board : boards.values()) {
            board.delete();
        }
        boards.clear();
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

    private record RenderedEntry(Component line, Component score) {
    }
}
