package com.donutsforlife11.donutgame.api.event;

import java.util.List;
import java.util.function.Predicate;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.api.item.GameItemHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayerHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.player.PlayerManagerHandle;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEvent<E extends Event> implements GameEventProperties<E> {
    private final E event;
    private final GameModule module;
    private final GameEventAdapterRegistry adapterRegistry;

    public GameEvent(E event, GameModule module, GameEventAdapterRegistry adapterRegistry) {
        this.event = event;
        this.module = module;
        this.adapterRegistry = adapterRegistry;
    }

    E event() {
        return event;
    }

    E bukkitEvent() {
        return event;
    }

    public GameModule module() {
        return module;
    }

    public Class<? extends Event> eventType() {
        return event.getClass();
    }

    public void clearDrops() {
        Object drops = getRaw("drops");
        if (drops instanceof List<?> list) {
            list.clear();
        }
    }

    public void removeBlocksIf(Predicate<? super GameBlock> predicate) {
        if (predicate == null) {
            return;
        }
        Object blocks = getRaw("blockList");
        if (!(blocks instanceof List<?> list)) {
            return;
        }
        list.removeIf(block -> block instanceof Block bukkitBlock
            && predicate.test(adapterRegistry.adapt(bukkitBlock, GameBlock.class, module)));
    }

    public void setSpectatorLocation(GameLocation location) {
        if (event instanceof GamePlayerLateJoinEvent lateJoinEvent) {
            lateJoinEvent.setSpectatorLocation(location);
        }
    }

    public GamePlayer getKiller() {
        if (!(event instanceof PlayerDeathEvent deathEvent)) {
            return null;
        }
        Player killer = deathEvent.getPlayer().getKiller();
        if (killer == null && deathEvent.getDamageSource().getCausingEntity() instanceof Player causingPlayer) {
            killer = causingPlayer;
        }
        return killer == null ? null : PlayerManagerHandle.getPlayer(module.playerManager(), killer);
    }

    public GameEventAdapterRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    Object getRaw(String property) {
        return GameEventReflection.get(event, property);
    }

    <T> T get(String property, Class<T> type) {
        return adapterRegistry.adapt(getRaw(property), type, module);
    }

    <T> List<T> getList(String property, Class<T> type) {
        Object raw = getRaw(property);
        if (!(raw instanceof List<?> list) || type == null) {
            return List.of();
        }
        return list.stream()
            .map(value -> adapterRegistry.adapt(value, type, module))
            .filter(value -> value != null)
            .toList();
    }

    void set(String property, Object value) {
        GameEventReflection.set(event, property, unwrap(value));
    }

    private Object unwrap(Object value) {
        if (value instanceof com.donutsforlife11.donutgame.api.player.GamePlayer player) {
            return GamePlayerHandle.bukkitPlayer(player);
        }
        if (value instanceof com.donutsforlife11.donutgame.api.entity.GameEntity entity) {
            return GameEntityHandle.bukkitEntity(entity);
        }
        if (value instanceof com.donutsforlife11.donutgame.api.map.GameLocation location) {
            return GameLocationHandle.bukkitLocation(location);
        }
        if (value instanceof com.donutsforlife11.donutgame.api.map.GameBlock block) {
            return GameLocationHandle.bukkitLocation(block.location()).getBlock();
        }
        if (value instanceof com.donutsforlife11.donutgame.api.map.GameWorld world) {
            return GameWorldHandle.bukkitWorld(world);
        }
        if (value instanceof com.donutsforlife11.donutgame.api.item.GameItem item) {
            return GameItemHandle.bukkitItem(item);
        }
        return value;
    }
}
