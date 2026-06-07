package com.donutsforlife11.donutgame.api.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public class GameSidebar {
    private static final char SECTION = '\u00A7';
    private static final int MAX_LINES = 15;
    private static final List<String> ENTRIES = IntStream.range(0, MAX_LINES)
        .mapToObj(index -> "" + SECTION + "0123456789abcde".charAt(index) + SECTION + 'r')
        .toList();

    private final UIManager manager;
    private final List<Object> lines = new ArrayList<>();
    private final Map<UUID, SidebarView> views = new LinkedHashMap<>();

    private Audience audience = Audience.empty();
    private int updateInterval = 1;
    private boolean visible;
    private boolean removed;
    private boolean dirty = true;
    private long lastRefreshTick = Long.MIN_VALUE;

    GameSidebar(UIManager manager) {
        this.manager = manager;
    }

    public Audience audience() {
        return audience;
    }

    public GameSidebar setAudience(Audience audience) {
        ensureUsable();
        this.audience = Objects.requireNonNull(audience, "audience");
        markDirty();
        return this;
    }

    public GameSidebar addLine(Component line) {
        ensureUsable();
        requireCapacity();
        lines.add(Objects.requireNonNull(line, "line"));
        markDirty();
        return this;
    }

    public GameSidebar addLine(ValueDisplay display) {
        ensureUsable();
        requireCapacity();
        lines.add(Objects.requireNonNull(display, "display"));
        markDirty();
        return this;
    }

    public GameSidebar removeLine(int index) {
        ensureUsable();
        lines.remove(index);
        markDirty();
        return this;
    }

    public GameSidebar clearLines() {
        ensureUsable();
        lines.clear();
        markDirty();
        return this;
    }

    public List<Object> getLines() {
        return List.copyOf(lines);
    }

    public GameSidebar setUpdateInterval(int interval) {
        ensureUsable();
        if (interval < 1) {
            throw new IllegalArgumentException("Sidebar update interval must be at least 1 tick.");
        }

        this.updateInterval = interval;
        markDirty();
        return this;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public GameSidebar setVisibility(boolean visible) {
        ensureUsable();
        this.visible = visible;
        if (visible) {
            manager.claimSidebar(this);
        }
        markDirty();
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public GameSidebar refresh() {
        ensureUsable();
        render(lastRefreshTick == Long.MIN_VALUE ? 0L : lastRefreshTick + 1L);
        return this;
    }

    public void remove() {
        if (removed) {
            return;
        }

        visible = false;
        removed = true;
        hidePlayers(Set.copyOf(manager.resolvePlayers(audience)));
        manager.remove(this);
    }

    boolean isActive() {
        return visible || !views.isEmpty() || dirty;
    }

    void pulse(long currentTick) {
        if (!isActive()) {
            return;
        }

        if (!visible) {
            hidePlayers(Set.copyOf(views.keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList()));
            dirty = false;
            return;
        }

        boolean dynamic = lines.stream().anyMatch(line -> line instanceof ValueDisplay display && display.isDynamic());
        if (dirty || !dynamic || currentTick - lastRefreshTick >= updateInterval) {
            render(currentTick);
        }
    }

    void hidePlayers(Set<Player> players) {
        for (Player player : players) {
            SidebarView view = views.remove(player.getUniqueId());
            if (view != null && player.getScoreboard() == view.scoreboard()) {
                player.setScoreboard(view.previousScoreboard());
            }
        }
    }

    void forceHidden() {
        visible = false;
        markDirty();
    }

    private void render(long currentTick) {
        Set<Player> players = manager.resolvePlayers(audience);

        views.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !players.contains(player)) {
                if (player != null && player.getScoreboard() == entry.getValue().scoreboard()) {
                    player.setScoreboard(entry.getValue().previousScoreboard());
                }
                return true;
            }
            return false;
        });

        for (Player player : players) {
            SidebarView view = views.computeIfAbsent(player.getUniqueId(), uuid -> createView(player));
            applyLines(view, player);
            if (player.getScoreboard() != view.scoreboard()) {
                player.setScoreboard(view.scoreboard());
            }
        }

        dirty = false;
        lastRefreshTick = currentTick;
    }

    private SidebarView createView(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Criteria criteria = Bukkit.getScoreboardCriteria("dummy");
        Objective objective = scoreboard.registerNewObjective(
            "donut_" + Integer.toUnsignedString(System.identityHashCode(this), 36),
            criteria,
            Component.empty(),
            RenderType.INTEGER
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        Map<Integer, Team> teams = new LinkedHashMap<>();
        for (int index = 0; index < MAX_LINES; index++) {
            Team team = scoreboard.registerNewTeam("line_" + index);
            String entry = ENTRIES.get(index);
            team.addEntry(entry);
            teams.put(index, team);
        }

        return new SidebarView(player.getScoreboard(), scoreboard, objective, teams, new ArrayList<>(MAX_LINES));
    }

    private void applyLines(SidebarView view, Player player) {
        List<Component> renderedLines = new ArrayList<>(lines.size());

        for (Object line : lines) {
            if (line instanceof Component component) {
                renderedLines.add(component);
            } else {
                renderedLines.add(((ValueDisplay) line).render(ValueSurface.SIDEBAR, manager.theme(), player));
            }
        }

        List<Component> previousLines = view.previousLines();

        for (int index = 0; index < MAX_LINES; index++) {
            Team team = view.teams().get(index);
            String entry = ENTRIES.get(index);
            Component next = index < renderedLines.size() ? renderedLines.get(index) : Component.empty();
            Component previous = index < previousLines.size() ? previousLines.get(index) : null;

            if (index < renderedLines.size()) {
                view.objective().getScore(entry).setScore(MAX_LINES - index);
            } else {
                view.scoreboard().resetScores(entry);
            }

            if (!Objects.equals(previous, next)) {
                team.prefix(next);
                team.suffix(Component.empty());
            }
        }

        previousLines.clear();
        previousLines.addAll(renderedLines);
    }

    private void requireCapacity() {
        if (lines.size() >= MAX_LINES) {
            throw new IllegalStateException("Sidebar cannot have more than " + MAX_LINES + " lines.");
        }
    }

    private void markDirty() {
        dirty = true;
        manager.markDirty();
    }

    private void ensureUsable() {
        if (removed) {
            throw new IllegalStateException("Sidebar has already been removed.");
        }
    }

    private record SidebarView(
        Scoreboard previousScoreboard,
        Scoreboard scoreboard,
        Objective objective,
        Map<Integer, Team> teams,
        List<Component> previousLines
    ) {
    }
}
