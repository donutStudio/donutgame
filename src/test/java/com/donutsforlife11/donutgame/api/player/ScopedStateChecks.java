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
        System.out.println("Passed 6 scoped state checks.");
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
