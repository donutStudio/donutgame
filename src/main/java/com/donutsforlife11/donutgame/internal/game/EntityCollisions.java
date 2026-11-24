package com.donutsforlife11.donutgame.internal.game;

import java.util.*;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;

/** Keeps Paper entities pickable while excluding physical pushes in their game world. */
public final class EntityCollisions {
    private record State(GameWorld world, Set<UUID> exemptions) { }
    private static final Map<LivingEntity, State> disabled = new WeakHashMap<>();
    private EntityCollisions() { }

    public static void set(GameWorld world, LivingEntity entity, boolean enabled) {
        clear(entity);
        entity.setCollidable(true);
        if (!enabled) {
            State state = new State(world, new HashSet<>());
            disabled.put(entity, state);
            update(entity, state, entity.getWorld().getLivingEntities());
        }
    }

    public static void clear(LivingEntity entity) {
        State state = disabled.remove(entity);
        if (state != null) entity.getCollidableExemptions().removeAll(state.exemptions());
    }

    public static void tick() {
        Map<World, List<LivingEntity>> entities = new HashMap<>();
        for (var entry : new ArrayList<>(disabled.entrySet())) {
            LivingEntity entity = entry.getKey();
            State state = entry.getValue();
            if (!entity.isValid() || !state.world().owner().acceptsLiveOperations()
                || entity.getWorld() != GameWorldHandle.bukkitWorld(state.world())) {
                clear(entity);
                continue;
            }
            update(entity, state, entities.computeIfAbsent(entity.getWorld(), World::getLivingEntities));
        }
    }

    private static void update(LivingEntity entity, State state, List<LivingEntity> nearby) {
        Set<UUID> current = new HashSet<>();
        for (LivingEntity other : nearby) if (other != entity) current.add(other.getUniqueId());
        for (UUID id : new HashSet<>(state.exemptions())) {
            if (!current.contains(id)) { entity.getCollidableExemptions().remove(id); state.exemptions().remove(id); }
        }
        for (UUID id : current) {
            if (entity.getCollidableExemptions().add(id)) state.exemptions().add(id);
        }
    }
}
