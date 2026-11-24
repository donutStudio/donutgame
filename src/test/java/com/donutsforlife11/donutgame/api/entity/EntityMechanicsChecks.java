package com.donutsforlife11.donutgame.api.entity;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import com.donutsforlife11.donutgame.internal.game.EntityCollisions;

public final class EntityMechanicsChecks {
    public static void main(String[] args) {
        var exemptions = new HashSet<UUID>();
        UUID otherId = UUID.randomUUID();
        LivingEntity other = (LivingEntity) Proxy.newProxyInstance(EntityMechanicsChecks.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
            (proxy, method, values) -> method.getName().equals("getUniqueId") ? otherId : null);
        World world = (World) Proxy.newProxyInstance(EntityMechanicsChecks.class.getClassLoader(), new Class<?>[]{World.class},
            (proxy, method, values) -> method.getName().equals("getLivingEntities") ? List.of(other) : null);
        boolean[] nativeCollidable = {false};
        LivingEntity npc = (LivingEntity) Proxy.newProxyInstance(EntityMechanicsChecks.class.getClassLoader(), new Class<?>[]{LivingEntity.class}, (proxy, method, values) -> {
            return switch (method.getName()) {
                case "getWorld" -> world;
                case "getCollidableExemptions" -> exemptions;
                case "setCollidable" -> { nativeCollidable[0] = (boolean) values[0]; yield null; }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == values[0];
                default -> null;
            };
        });
        EntityCollisions.set(null, npc, false);
        if (!nativeCollidable[0] || !exemptions.contains(otherId)) throw new AssertionError("No-push entities must remain pickable");
        EntityCollisions.set(null, npc, true);
        if (!exemptions.isEmpty()) throw new AssertionError("Restoring collisions must clear owned exemptions");
        var goal = new BoundingBox(0, 0, 0, 7, 7, 7);
        if (!goal.overlaps(new BoundingBox(6.9, 1, 1, 7.9, 2, 2))) throw new AssertionError("Partial hitbox intersection scores");
        if (goal.overlaps(new BoundingBox(7, 1, 1, 8, 2, 2))) throw new AssertionError("Touching the edge is not intersection");
        if (goal.overlaps(new BoundingBox(7.1, 1, 1, 8.1, 2, 2))) throw new AssertionError("No extra goal radius");
        if (goal.overlaps(new BoundingBox(1, 7.1, 1, 2, 8.1, 2))) throw new AssertionError("Height is bounded too");
        System.out.println("Passed non-pushing/pickable collision and exact goal hitbox checks.");
    }
}
