package com.donutsforlife11.donutgame.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GameEventAdapterRegistry {
    private final List<AdapterEntry<?, ?>> adapters = new ArrayList<>();

    public static GameEventAdapterRegistry defaults() {
        GameEventAdapterRegistry registry = new GameEventAdapterRegistry();
        registry.register(Player.class, GamePlayer.class, (player, module) -> module.playerManager().getPlayer(player));
        registry.register(Entity.class, GameEntity.class, (entity, module) -> {
            GameWorld world = gameWorld(module, entity.getWorld());
            return world == null ? null : new GameEntity(world, entity);
        });
        registry.register(Location.class, GameLocation.class, (location, module) -> {
            GameWorld world = gameWorld(module, location.getWorld());
            return world == null ? null : new GameLocation(world, location);
        });
        registry.register(World.class, GameWorld.class, (world, module) -> gameWorld(module, world));
        registry.register(ItemStack.class, GameItem.class, (item, module) -> GameItem.from(item));
        return registry;
    }

    public static List<TypeMapping> defaultTypeMappings() {
        return List.of(
            new TypeMapping(Player.class.getName(), GamePlayer.class.getName()),
            new TypeMapping(Entity.class.getName(), GameEntity.class.getName()),
            new TypeMapping(Location.class.getName(), GameLocation.class.getName()),
            new TypeMapping(World.class.getName(), GameWorld.class.getName()),
            new TypeMapping(ItemStack.class.getName(), GameItem.class.getName())
        );
    }

    public <S, T> void register(Class<S> sourceType, Class<T> targetType, GameEventAdapter<? super S, ? extends T> adapter) {
        adapters.add(new AdapterEntry<>(
            Objects.requireNonNull(sourceType, "sourceType"),
            Objects.requireNonNull(targetType, "targetType"),
            Objects.requireNonNull(adapter, "adapter")
        ));
    }

    public <T> T adapt(Object source, Class<T> targetType, GameModule module) {
        if (source == null || targetType == null) {
            return null;
        }
        if (targetType.isInstance(source)) {
            return targetType.cast(source);
        }
        for (AdapterEntry<?, ?> entry : adapters) {
            T adapted = entry.tryAdapt(source, targetType, module);
            if (adapted != null) {
                return adapted;
            }
        }
        return null;
    }

    private static GameWorld gameWorld(GameModule module, World world) {
        if (module == null || world == null) {
            return null;
        }
        for (GameWorld gameWorld : module.worlds().values()) {
            if (world.equals(gameWorld.bukkitWorld())) {
                return gameWorld;
            }
        }
        return null;
    }

    private record AdapterEntry<S, T>(
        Class<S> sourceType,
        Class<T> targetType,
        GameEventAdapter<? super S, ? extends T> adapter
    ) {
        private <R> R tryAdapt(Object source, Class<R> requestedType, GameModule module) {
            if (!sourceType.isInstance(source) || !requestedType.isAssignableFrom(targetType)) {
                return null;
            }
            T adapted = adapter.adapt(sourceType.cast(source), module);
            return adapted == null ? null : requestedType.cast(adapted);
        }
    }

    public record TypeMapping(String sourceType, String targetType) {
    }
}
