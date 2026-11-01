package com.donutsforlife11.donutgame.api.player;

import java.util.List;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class PlayerAttributeDefaults {
    private PlayerAttributeDefaults() {
    }

    public static double baseValue(AttributeInstance instance) {
        Attributable defaults = EntityType.PLAYER.getDefaultAttributes();
        AttributeInstance playerDefault = defaults == null ? null : defaults.getAttribute(instance.getAttribute());
        if (playerDefault != null) {
            return playerDefault.getBaseValue();
        }
        return instance.getAttribute().getDefaultValue();
    }

    public static double snapshotBaseValue(AttributeInstance instance) {
        double baseValue = instance.getBaseValue();
        return isInvalidCameraDistance(instance.getAttribute(), baseValue) ? baseValue(instance) : baseValue;
    }

    public static void restoreVanillaBase(AttributeInstance instance) {
        clearModifiers(instance);
        instance.setBaseValue(baseValue(instance));
    }

    public static void repairInvalidCameraDistance(Player player) {
        AttributeInstance instance = player == null ? null : player.getAttribute(Attribute.CAMERA_DISTANCE);
        if (instance == null || (instance.getBaseValue() > 0.0 && instance.getValue() > 0.0)) {
            return;
        }
        restoreVanillaBase(instance);
    }

    public static boolean isInvalidCameraDistance(Attribute attribute, double baseValue) {
        return attribute == Attribute.CAMERA_DISTANCE && baseValue <= 0.0;
    }

    public static void clearModifiers(AttributeInstance instance) {
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            instance.removeModifier(modifier);
        }
    }

}
