package com.donutsforlife11.donutgame.api.border;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.MapManager;
import com.donutsforlife11.donutgame.game.GameContext;

public class BorderManager {
    private final GameContext context;
    private final MapManager mapManager;
    private final Set<GameBorder> borders = ConcurrentHashMap.newKeySet();
    private int nextIndex = 0;

    public enum BorderShape {
        CUBOID,
        CYLINDROID,
        ELLIPSOID
    }

    public BorderManager(GameContext context, MapManager mapManager) {
        this.context = Objects.requireNonNull(context, "context");
        this.mapManager = Objects.requireNonNull(mapManager, "mapManager");
    }

    public Collection<GameBorder> getBorders() {
        return List.copyOf(borders);
    }

    public GameBorder createBorder(BorderShape shape, Location center, Vector dimensions) {
        GameBorder border = new GameBorder(this, shape, center, dimensions);
        borders.add(border);
        return border;
    }
    protected void deleteBorder(GameBorder border) {
        if (border == null) {
            return;
        }
        borders.remove(border);
    }
}
