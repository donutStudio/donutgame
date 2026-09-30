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
        AttributeInstance playerDefault = defaultPlayerAttribute(instance.getAttribute());
        if (playerDefault != null) {
            return playerDefault.getBaseValue();
        }
        return defaultValue(instance);
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
        if (instance == null || !isInvalidCameraDistance(instance.getAttribute(), instance.getBaseValue(), instance.getValue())) {
            return;
        }
        restoreVanillaBase(instance);
    }

    public static boolean isInvalidCameraDistance(Attribute attribute, double baseValue) {
        return isCameraDistance(attribute) && baseValue <= 0.0;
    }

    public static void clearModifiers(AttributeInstance instance) {
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            instance.removeModifier(modifier);
        }
    }

    private static boolean isInvalidCameraDistance(Attribute attribute, double baseValue, double value) {
        return isCameraDistance(attribute) && (baseValue <= 0.0 || value <= 0.0);
    }

    private static boolean isCameraDistance(Attribute attribute) {
        return attribute == Attribute.CAMERA_DISTANCE;
    }

    private static AttributeInstance defaultPlayerAttribute(Attribute attribute) {
        Attributable playerDefaults = EntityType.PLAYER.getDefaultAttributes();
        return playerDefaults == null ? null : playerDefaults.getAttribute(attribute);
    }

    @SuppressWarnings("deprecation")
    private static double defaultValue(AttributeInstance instance) {
        return instance.getDefaultValue();
    }
}
