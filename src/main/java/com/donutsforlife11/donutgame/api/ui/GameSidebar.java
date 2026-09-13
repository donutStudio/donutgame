package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private static final int DEFAULT_REFRESH_INTERVAL = 10;

    private final GameModule module;
    private final List<SidebarEntry> entries = new ArrayList<>();
    private final Map<UUID, FastBoard> boards = new LinkedHashMap<>();

    private Supplier<Collection<GamePlayer>> viewers;
    private Supplier<Component> title;
    private GameTimer refreshTimer;
    private int refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private boolean visible;

    GameSidebar(GameModule module) {
        this.module = module;
        this.viewers = () -> List.copyOf(module.playerManager().getPlayers());
        this.title = () -> Component.text(module.name(), NamedTextColor.GOLD, TextDecoration.BOLD)
            .shadowColor(ShadowColor.shadowColor(0, 0, 0, 128));
    }

    public GameSidebar setViewers(Collection<GamePlayer> viewers) {
        this.viewers = () -> viewers == null ? List.of() : List.copyOf(viewers);
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar setViewers(Supplier<Collection<GamePlayer>> viewers) {
        this.viewers = Objects.requireNonNull(viewers);
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar setTitle(Component title) {
        this.title = () -> title;
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar setTitle(Supplier<Component> title) {
        this.title = Objects.requireNonNull(title);
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar addEntry(SidebarEntry entry) {
        entries.add(Objects.requireNonNull(entry));
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar insertEntry(int index, SidebarEntry entry) {
        entries.add(index, Objects.requireNonNull(entry));
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar setEntry(int index, SidebarEntry entry) {
        entries.set(index, Objects.requireNonNull(entry));
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar replaceEntry(SidebarEntry oldEntry, SidebarEntry newEntry) {
        int index = entries.indexOf(oldEntry);
        if (index >= 0) {
            setEntry(index, newEntry);
        }
        return this;
    }

    public GameSidebar removeEntry(SidebarEntry entry) {
        entries.remove(entry);
        if (visible) {
            refresh();
        }
        return this;
    }

    public GameSidebar removeEntry(int index) {
        entries.remove(index);
        if (visible) {
            refresh();
        }
        return this;
    }

    public List<SidebarEntry> entries() {
        return List.copyOf(entries);
    }

    public GameSidebar show() {
        if (visible) {
            return this;
        }
        visible = true;
        refresh();
        startRefreshTimer();
        return this;
    }

    public GameSidebar hide() {
        visible = false;
        stopRefreshTimer();
        for (FastBoard board : boards.values()) {
            board.delete();
        }
        boards.clear();
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public GameSidebar setRefreshInterval(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Sidebar refresh interval cannot be negative.");
        }
        refreshInterval = ticks;
        if (visible) {
            stopRefreshTimer();
            startRefreshTimer();
        }
        return this;
    }

    public GameSidebar refresh() {
        if (!visible) {
            return this;
        }

        Map<UUID, GamePlayer> currentViewers = currentViewers();
        boards.entrySet().removeIf(entry -> {
            if (currentViewers.containsKey(entry.getKey())) {
                return false;
            }
            entry.getValue().delete();
            return true;
        });

        for (GamePlayer viewer : currentViewers.values()) {
            FastBoard board = boards.computeIfAbsent(viewer.uuid(), ignored -> new FastBoard(viewer.bukkitPlayer()));
            board.updateTitle(SidebarEntry.safe(title.get()));
            List<Component> lines = new ArrayList<>(entries.size() + 2);
            List<Component> scores = new ArrayList<>(entries.size() + 2);
            lines.add(Component.empty());
            scores.add(Component.empty());
            for (SidebarEntry entry : entries) {
                SidebarEntry.RenderedEntry rendered = entry.render(viewer);
                lines.add(SidebarEntry.safe(rendered.line()));
                scores.add(SidebarEntry.safe(rendered.score()));
            }
            lines.add(Component.empty());
            scores.add(Component.empty());
            board.updateLines(lines, scores);
        }
        return this;
    }

    private void startRefreshTimer() {
        if (refreshInterval == 0 || refreshTimer != null) {
            return;
        }
        refreshTimer = module.timeManager().newTimer()
            .onTick(refreshInterval, ignored -> refresh())
            .start();
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }

    private Map<UUID, GamePlayer> currentViewers() {
        Map<UUID, GamePlayer> currentViewers = new LinkedHashMap<>();
        Collection<GamePlayer> supplied = viewers.get();
        if (supplied == null) {
            return currentViewers;
        }
        for (GamePlayer viewer : supplied) {
            if (viewer != null && viewer.bukkitPlayer() != null) {
                currentViewers.put(viewer.uuid(), viewer);
            }
        }
        return currentViewers;
    }
}
