package com.donutsforlife11.donutgame.api.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class GameBossbar {
    // TODO: The game updates this bar when its clock or state changes; the UI owns no ticking task.
    private final GameModule module;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private final Map<UUID, org.bukkit.entity.Player> viewers = new HashMap<>();

    GameBossbar(GameModule module) { this.module = module; }

    public GameBossbar setTitle(Component title) { bar.name(title); return this; }
    public GameBossbar setProgress(float progress) {
        bar.progress(Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0);
        return this;
    }
    public GameBossbar setColor(BossBar.Color color) { bar.color(color); return this; }

    public GameBossbar show(Collection<GamePlayer> players) {
        GameScope.requireActive(module);
        var next = new HashMap<UUID, org.bukkit.entity.Player>();
        for (GamePlayer player : players) {
            GameScope.requirePlayer(module, player);
            var online = GamePlayerHandle.bukkitPlayer(player);
            if (online != null) next.put(player.uuid(), online);
        }
        viewers.forEach((id, player) -> { if (next.get(id) != player) player.hideBossBar(bar); });
        next.forEach((id, player) -> { if (viewers.get(id) != player) player.showBossBar(bar); });
        viewers.clear();
        viewers.putAll(next);
        return this;
    }

    public GameBossbar hide() {
        viewers.values().forEach(player -> player.hideBossBar(bar));
        viewers.clear();
        return this;
    }

    void hide(GamePlayer player) {
        var viewer = viewers.remove(player.uuid());
        if (viewer != null) viewer.hideBossBar(bar);
    }
}
