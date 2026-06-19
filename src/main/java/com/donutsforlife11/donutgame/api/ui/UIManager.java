package com.donutsforlife11.donutgame.api.ui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.api.ui.title.TitlePacketTracker;

import fr.skytasul.glowingentities.GlowingBlocks;
import fr.skytasul.glowingentities.GlowingEntities;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.title.Title;

public class UIManager {
    private final Plugin plugin;
    private final UITheme theme = new UITheme();
    private final Set<GameSidebar> sidebars = ConcurrentHashMap.newKeySet();
    private final Set<GameBossbar> bossbars = ConcurrentHashMap.newKeySet();
    private final Set<GameGlow> glows = ConcurrentHashMap.newKeySet();
    private final TitlePacketTracker titlePacketTracker;

    private final Object taskLock = new Object();

    private volatile BukkitTask task;
    private volatile boolean shutdown;
    private volatile long tick;

    private GlowingEntities glowingEntities;
    private GlowingBlocks glowingBlocks;

    public UIManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.titlePacketTracker = new TitlePacketTracker(plugin);
        this.glowingEntities = new GlowingEntities(plugin);
        this.glowingBlocks = new GlowingBlocks(plugin);
    }

    public void title(Audience audience, Component title) {
        requireActive();
        audience.sendTitlePart(TitlePart.TITLE, requireComponent(title));
    }

    public void subtitle(Audience audience, Component subtitle) {
        subtitle(audience, subtitle, Title.Times.times(
                Duration.ofMillis(250),
                Duration.ofSeconds(2),
                Duration.ofMillis(250)
        ));
    }

    public void subtitle(Audience audience, Component subtitle, Title.Times times) {
        requireActive();

        Component safeSubtitle = requireComponent(subtitle);
        Set<Player> players = resolvePlayers(audience);

        if (players.isEmpty()) {
            audience.sendTitlePart(TitlePart.SUBTITLE, safeSubtitle);
            return;
        }

        for (Player player : players) {
            if (!titlePacketTracker.available()) {
                player.sendTitlePart(TitlePart.SUBTITLE, safeSubtitle);
                continue;
            }

            if (titlePacketTracker.hasActiveTitle(player)) {
                player.sendTitlePart(TitlePart.SUBTITLE, safeSubtitle);
            } else {
                player.showTitle(Title.title(Component.empty(), safeSubtitle, times));
            }
        }
    }

    public void actionbar(Audience audience, Component message) {
        requireActive();
        audience.sendActionBar(requireComponent(message));
    }

    public void chat(Audience audience, Component message) {
        requireActive();
        audience.sendMessage(requireComponent(message));
    }

    public void gameMessage(Audience audience, Component message) {
        requireActive();
        audience.sendMessage(Component.text("Game > ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
            .append(Component.empty().append(requireComponent(message))));
    }

    public GameSidebar createSidebar() {
        requireActive();
        GameSidebar sidebar = new GameSidebar(this);
        sidebars.add(sidebar);
        return sidebar;
    }

    public GameBossbar createBossbar() {
        requireActive();
        GameBossbar bossbar = new GameBossbar(this);
        bossbars.add(bossbar);
        return bossbar;
    }

    public GameGlow createGlow() {
        requireActive();
        GameGlow glow = new GameGlow(this);
        glows.add(glow);
        return glow;
    }

    public void clearUi(Audience audience) {
        requireActive();

        audience.clearTitle();
        audience.sendActionBar(Component.empty());

        Set<Player> players = resolvePlayers(audience);
        players.forEach(titlePacketTracker::forget);
        if (players.isEmpty()) {
            return;
        }

        sidebars.forEach(sidebar -> sidebar.hidePlayers(players));
        bossbars.forEach(bossbar -> bossbar.hidePlayers(players));
    }

    protected GlowingEntities glowingEntities() {
        return glowingEntities;
    }
    protected GlowingBlocks glowingBlocks() {
        return glowingBlocks;
    }

    public UITheme theme() {
        return theme;
    }

    public void shutdown() {
        shutdown = true;

        BukkitTask runningTask = task;
        if (runningTask != null) {
            runningTask.cancel();
            task = null;
        }

        List.copyOf(sidebars).forEach(GameSidebar::remove);
        List.copyOf(bossbars).forEach(GameBossbar::remove);
        sidebars.clear();
        bossbars.clear();
    }

    void remove(GameSidebar sidebar) {
        sidebars.remove(sidebar);
        stopTaskIfIdle();
    }

    void remove(GameBossbar bossbar) {
        bossbars.remove(bossbar);
        stopTaskIfIdle();
    }

    void remove(GameGlow glow) {
        glows.remove(glow);
        stopTaskIfIdle();
    }

    void markDirty() {
        requireActive();
        ensureTask();
    }

    void claimSidebar(GameSidebar sidebar) {
        for (GameSidebar other : List.copyOf(sidebars)) {
            if (other != sidebar && other.isVisible()) {
                other.forceHidden();
            }
        }
    }

    Set<Player> resolvePlayers(Audience audience) {
        Set<Player> players = new LinkedHashSet<>();
        collectPlayers(Objects.requireNonNull(audience, "audience"), players);
        return players;
    }

    private void requireActive() {
        if (shutdown || !plugin.isEnabled()) {
            throw new IllegalStateException("UIManager is not active.");
        }
    }

    private Component requireComponent(Component component) {
        return Objects.requireNonNull(component, "component");
    }

    private void ensureTask() {
        synchronized (taskLock) {
            if (task != null) {
                return;
            }

            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUi, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        synchronized (taskLock) {
            if (task == null || hasActiveElements()) {
                return;
            }

            task.cancel();
            task = null;
        }
    }

    private boolean hasActiveElements() {
        return sidebars.stream().anyMatch(GameSidebar::isActive) || bossbars.stream().anyMatch(GameBossbar::isActive);
    }

    private void tickUi() {
        if (shutdown || !plugin.isEnabled()) {
            shutdown();
            return;
        }

        tick++;

        for (GameSidebar sidebar : List.copyOf(sidebars)) {
            sidebar.pulse(tick);
        }

        for (GameBossbar bossbar : List.copyOf(bossbars)) {
            bossbar.pulse(tick);
        }

        stopTaskIfIdle();
    }

    private void collectPlayers(Audience audience, Set<Player> players) {
        if (audience instanceof Player player) {
            players.add(player);
            return;
        }

        if (audience instanceof ForwardingAudience.Single single) {
            collectPlayers(single.audience(), players);
            return;
        }

        if (audience instanceof ForwardingAudience forwarding) {
            for (Audience child : forwarding.audiences()) {
                collectPlayers(child, players);
            }
        }
    }
}
