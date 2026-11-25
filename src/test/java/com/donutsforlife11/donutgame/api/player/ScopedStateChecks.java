package com.donutsforlife11.donutgame.api.player;

import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.inventory.EquipmentSlotGroup;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameRegion;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public final class ScopedStateChecks {
    public static void main(String[] args) {
        GameModule first = new GameModule() {};
        GameModule second = new GameModule() {};
        GameLocation coordinate = new GameLocation(2, 3, 4);
        GameLocation bound = coordinate.inWorld(first.world());
        check(bound.world() == first.world() && bound.x() == 2, "Worldless coordinates bind to the receiver");
        check(bound.inWorld(first.world()) == bound, "Binding to the same world preserves generation");
        rejects(() -> bound.inWorld(second.world()));
        rejects(() -> new GameRegion(bound, coordinate.inWorld(second.world())));

        var add = modifier("add", 2, Operation.ADD_NUMBER);
        var scalar = modifier("scalar", 0.5, Operation.ADD_SCALAR);
        var multiply = modifier("multiply", 0.25, Operation.MULTIPLY_SCALAR_1);
        check(PlayerSnapshot.attributeValue(10, List.of(multiply, scalar, add)) == 22.5,
            "Virtual attributes apply addition, scalar and multiplier in vanilla order");
        check(PlayerSnapshot.attributeValue(10, List.of(add, multiply, scalar)) == 22.5,
            "Modifier insertion order does not change the value");
        var player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(ScopedStateChecks.class.getClassLoader(),
            new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, values) -> {
                if (method.getName().equals("setWalkSpeed") || method.getName().equals("setFlySpeed"))
                    throw new AssertionError("Respawn/spectator restoration must not overwrite movement speeds");
                return null;
            });
        PlayerSnapshot state = PlayerSnapshot.empty();
        for (int respawn = 0; respawn < 8; respawn++) {
            state.restoreAliveDefaults(20);
            SpectatorProjection.applyNonSpectatorOverlay(player, state);
        }
        int[] callbacks = {0};
        state.health = 0;
        GameLocation prepared = state.prepareRespawn(20, () -> {
            callbacks[0]++;
            check(state.health == 20, "Alive defaults must precede the game respawn callback");
            state.foodLevel = 12;
            return coordinate;
        });
        check(prepared == coordinate && callbacks[0] == 1 && state.foodLevel == 12,
            "Respawn callbacks run once and their newly prepared state is preserved");
        System.out.println("Passed scoped state, repeated respawn speed, and callback ordering checks.");
    }

    private static AttributeModifier modifier(String key, double amount, Operation op) {
        return new AttributeModifier(new NamespacedKey("donutgame_test", key), amount, op, EquipmentSlotGroup.ANY);
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a scope error");
        } catch (IllegalArgumentException expected) {
            // Expected: the source already belongs to another world.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
