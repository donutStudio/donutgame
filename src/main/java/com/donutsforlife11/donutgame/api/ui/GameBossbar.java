package com.donutsforlife11.donutgame.api.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public class GameBossbar {
    private final UIManager manager;
    private final Map<UUID, BossBar> bars = new LinkedHashMap<>();

    private Audience audience = Audience.empty();
    private Object title = Component.empty();
    private Object value = 0;
    private Object max = 100;
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Overlay style = BossBar.Overlay.PROGRESS;
    private int updateInterval = 1;

    private boolean visible;
    private boolean removed;
    private boolean dirty = true;
    private long lastRefreshTick = Long.MIN_VALUE;

    GameBossbar(UIManager manager) {
        this.manager = manager;
    }

    public Audience audience() {
        return audience;
    }

    public GameBossbar setAudience(Audience audience) {
        ensureUsable();
        this.audience = Objects.requireNonNull(audience, "audience");
        markDirty();
        return this;
    }

    public Component getTitle() {
        ensureUsable();
        return resolveTitle(null);
    }

    public GameBossbar setTitle(Component title) {
        ensureUsable();
        this.title = Objects.requireNonNull(title, "title");
        markDirty();
        return this;
    }

    public GameBossbar setTitle(ValueDisplay display) {
        ensureUsable();
        this.title = Objects.requireNonNull(display, "display");
        markDirty();
        return this;
    }

    public int getValue() {
        ensureUsable();
        return resolveNumeric(value, null, "value");
    }

    public GameBossbar setValue(int value) {
        ensureUsable();
        this.value = value;
        markDirty();
        return this;
    }

    public GameBossbar setValue(ValueDisplay value) {
        ensureUsable();
        this.value = Objects.requireNonNull(value, "value");
        markDirty();
        return this;
    }

    public int getMax() {
        ensureUsable();
        return resolveNumeric(max, null, "max");
    }

    public GameBossbar setMax(int max) {
        ensureUsable();
        if (max < 1) {
            throw new IllegalArgumentException("Bossbar max must be at least 1.");
        }

        this.max = max;
        markDirty();
        return this;
    }

    public GameBossbar setMax(ValueDisplay max) {
        ensureUsable();
        this.max = Objects.requireNonNull(max, "max");
        markDirty();
        return this;
    }

    public GameBossbar setUpdateInterval(int interval) {
        ensureUsable();
        if (interval < 1) {
            throw new IllegalArgumentException("Bossbar update interval must be at least 1 tick.");
        }

        this.updateInterval = interval;
        markDirty();
        return this;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public GameBossbar setStyle(BossBar.Overlay style) {
        ensureUsable();
        this.style = Objects.requireNonNull(style, "style");
        markDirty();
        return this;
    }

    public BossBar.Overlay getStyle() {
        return style;
    }

    public GameBossbar setColor(BossBar.Color color) {
        ensureUsable();
        this.color = Objects.requireNonNull(color, "color");
        markDirty();
        return this;
    }

    public BossBar.Color getColor() {
        return color;
    }

    public GameBossbar setVisibility(boolean visible) {
        ensureUsable();
        this.visible = visible;
        markDirty();
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public GameBossbar refresh() {
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
        return visible || !bars.isEmpty() || dirty;
    }

    void pulse(long currentTick) {
        if (!isActive()) {
            return;
        }

        if (!visible) {
            hidePlayers(Set.copyOf(bars.keySet().stream().map(org.bukkit.Bukkit::getPlayer).filter(Objects::nonNull).toList()));
            dirty = false;
            return;
        }

        boolean dynamic = isDynamic();
        if (dirty || !dynamic || currentTick - lastRefreshTick >= updateInterval) {
            render(currentTick);
        }
    }

    void hidePlayers(Set<Player> players) {
        for (Player player : players) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
    }

    private void render(long currentTick) {
        Set<Player> players = manager.resolvePlayers(audience);
        bars.entrySet().removeIf(entry -> {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player == null || !players.contains(player)) {
                if (player != null) {
                    player.hideBossBar(entry.getValue());
                }
                return true;
            }
            return false;
        });

        for (Player player : players) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), uuid -> {
                BossBar created = BossBar.bossBar(Component.empty(), 0.0f, color, style);
                player.showBossBar(created);
                return created;
            });

            int resolvedMax = Math.max(1, resolveNumeric(max, player, "max"));
            int resolvedValue = Math.max(0, resolveNumeric(value, player, "value"));

            bar.name(resolveTitle(player));
            bar.color(color);
            bar.overlay(style);
            bar.progress(Math.min(1.0f, resolvedValue / (float) resolvedMax));
        }

        dirty = false;
        lastRefreshTick = currentTick;
    }

    private boolean isDynamic() {
        return isDynamic(title) || isDynamic(value) || isDynamic(max);
    }

    private boolean isDynamic(Object source) {
        return source instanceof ValueDisplay display && display.isDynamic();
    }

    private Component resolveTitle(Player player) {
        if (title instanceof Component component) {
            return component;
        }

        return ((ValueDisplay) title).render(ValueSurface.BOSSBAR, manager.theme(), player);
    }

    private int resolveNumeric(Object source, Player player, String name) {
        if (source instanceof Integer integer) {
            return integer;
        }

        try {
            return ((ValueDisplay) source).getNumericValue(player);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Bossbar " + name + " could not be resolved.", exception);
        }
    }

    private void markDirty() {
        dirty = true;
        manager.markDirty();
    }

    private void ensureUsable() {
        if (removed) {
            throw new IllegalStateException("Bossbar has already been removed.");
        }
    }
}
