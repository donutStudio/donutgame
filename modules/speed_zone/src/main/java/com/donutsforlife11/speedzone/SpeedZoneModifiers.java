package com.donutsforlife11.speedzone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

final class SpeedZoneModifiers {
    private final Map<String, Modifier> modifiers = new LinkedHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    SpeedZoneModifiers() {
        registerDefaults();
    }

    private void registerDefaults() {
        register("elytra", "minecraft:items", "item/elytra", player -> {
            GameItem elytra = GameItem.of(Material.ELYTRA);
            elytra.editMeta(meta -> meta.setUnbreakable(true));
            player.setItem(EquipmentSlot.CHEST, elytra);
        });

        register("riptide", "minecraft:items", "item/trident", player -> {
            GameItem trident = GameItem.of(Material.TRIDENT);
            trident.editMeta(meta -> meta.setUnbreakable(true));
            trident.addUnsafeEnchantment(Enchantment.RIPTIDE, 3);
            if (player.getItem(EquipmentSlot.HAND) == null) {
                player.setItem(EquipmentSlot.HAND, trident);
            } else {
                player.giveItem(trident);
            }
        });

        register("lunge", "minecraft:items", "item/iron_spear", player -> {
            GameItem spear = GameItem.of(Material.IRON_SPEAR);
            spear.editMeta(meta -> meta.setUnbreakable(true));
            spear.addUnsafeEnchantment(Enchantment.LUNGE, 3);
            if (player.getItem(EquipmentSlot.HAND) == null) {
                player.setItem(EquipmentSlot.HAND, spear);
            } else {
                player.giveItem(spear);
            }
        });

        register("wind_burst", "minecraft:items", "item/mace", player -> {
            GameItem mace = GameItem.of(Material.MACE);
            mace.editMeta(meta -> meta.setUnbreakable(true));
            mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 3);
            if (player.getItem(EquipmentSlot.HAND) == null) {
                player.setItem(EquipmentSlot.HAND, mace);
            } else {
                player.giveItem(mace);
            }
        });

        register("jump_boost", "minecraft:items", "item/rabbit_foot", player ->
            player.addEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 4, false, false, false)));

        register("boat", "minecraft:items", "item/oak_boat", player -> {
            GameItem boat = GameItem.of(Material.OAK_BOAT);
            if (player.getItem(EquipmentSlot.HAND) == null) {
                player.setItem(EquipmentSlot.HAND, boat);
            } else {
                player.giveItem(boat);
            }
        });

        register("bridge", "minecraft:blocks", "block/white_wool", player -> {
            GameItem currentHand = player.getItem(EquipmentSlot.HAND);
            if (currentHand != null) {
                player.giveItem(currentHand);
            }
            GameItem wool = GameItem.of(Material.WHITE_WOOL);
            wool.setAmount(65);
            wool.setData(GameItemComponents.INFINITE_BUILD, (byte) 1);
            wool.setData(GameItemComponents.TEAM_SYNC, (byte) 1);

            GameItem shears = GameItem.of(Material.SHEARS);
            shears.editMeta(meta -> meta.setUnbreakable(true));
            shears.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);

            player.setItem(EquipmentSlot.OFF_HAND, wool);
            player.setItem(EquipmentSlot.HAND, shears);
        });
    }

    void register(String key, String atlas, String sprite, Consumer<GamePlayer> apply) {
        String normalized = normalize(key);
        modifiers.put(normalized, new Modifier(normalized, atlas, sprite, apply));
    }

    boolean exists(String key) {
        return modifiers.containsKey(normalize(key));
    }

    void clear(GamePlayer player) {
        player.clearItems();
        player.clearEffect(PotionEffectType.JUMP_BOOST);
    }

    void apply(GamePlayer player, List<String> keys) {
        // Give non-bridge tools first; bridge intentionally owns the active hands when combined.
        for (String key : keys) {
            Modifier modifier = modifiers.get(normalize(key));
            if (modifier != null && !modifier.key().equals("bridge")) {
                modifier.apply().accept(player);
            }
        }
        if (keys.stream().map(this::normalize).anyMatch("bridge"::equals)) {
            modifiers.get("bridge").apply().accept(player);
        }
    }

    Component checkpointSubtitle(List<String> keys) {
        Component result = Component.text()
            .append(Component.text("✔", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            .append(Component.text(" Checkpoint Complete! ", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
        .build();
        List<Modifier> active = keys.stream()
            .map(this::normalize)
            .map(modifiers::get)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (active.isEmpty()) {
            return result;
        }

        result = result.append(Component.text(" [", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < active.size(); i++) {
            if (i > 0) {
                result = result.append(Component.space());
            }
            Modifier modifier = active.get(i);
            result = result.append(miniMessage.deserialize("<sprite:\"" + modifier.atlas() + "\":" + modifier.sprite() + ">"));
        }
        return result.append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private record Modifier(String key, String atlas, String sprite, Consumer<GamePlayer> apply) {
    }
}
