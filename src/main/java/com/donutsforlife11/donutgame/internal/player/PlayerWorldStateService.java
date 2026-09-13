package com.donutsforlife11.donutgame.internal.player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public class PlayerWorldStateService implements Listener {
    private static final Map<Attribute, Double> VANILLA_PLAYER_ATTRIBUTES = vanillaPlayerAttributes();

    private final Map<UUID, Map<UUID, PlayerWorldState>> statesByPlayer = new ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        save(player, event.getFrom());
        restoreOrReset(player, player.getWorld());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        restoreOrReset(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        save(event.getPlayer(), event.getPlayer().getWorld());
    }

    public void forgetWorld(World world) {
        if (world != null) {
            forgetWorld(world.getUID());
        }
    }

    public void forgetWorld(UUID worldId) {
        if (worldId == null) {
            return;
        }
        for (Map<UUID, PlayerWorldState> states : statesByPlayer.values()) {
            states.remove(worldId);
        }
        statesByPlayer.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void save(Player player, World world) {
        if (player == null || world == null) {
            return;
        }
        statesByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(world.getUID(), PlayerWorldState.capture(player));
    }

    private void restoreOrReset(Player player, World world) {
        if (player == null || world == null) {
            return;
        }
        PlayerWorldState state = statesByPlayer
            .getOrDefault(player.getUniqueId(), Map.of())
            .get(world.getUID());
        if (state == null) {
            applyEmptyState(player, world);
        } else {
            state.apply(player);
        }
        player.updateInventory();
    }

    private static void applyEmptyState(Player player, World world) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[1]);
        player.getEnderChest().clear();

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.05f);
        player.setWalkSpeed(0.2f);
        player.setSneaking(false);
        player.setSprinting(false);
        player.setVelocity(new Vector());

        player.clearActivePotionEffects();
        resetAttributes(player);
        player.setHealth(20.0);
        player.setAbsorptionAmount(0.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0f);
        player.setRemainingAir(player.getMaximumAir());
        player.setArrowsInBody(0);
        player.setNoDamageTicks(0);
        player.setPortalCooldown(0);
        player.setRespawnLocation(world.getSpawnLocation(), true);
    }

    private static void resetAttributes(Player player) {
        for (Attribute attribute : allAttributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                instance.removeModifier(modifier);
            }
            instance.setBaseValue(defaultBaseValue(attribute, instance));
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = items[i] == null ? null : items[i].clone();
        }
        return clone;
    }

    private static Map<Attribute, Double> vanillaPlayerAttributes() {
        Map<Attribute, Double> defaults = new HashMap<>();
        defaults.put(Attribute.MAX_HEALTH, 20.0);
        defaults.put(Attribute.FOLLOW_RANGE, 32.0);
        defaults.put(Attribute.KNOCKBACK_RESISTANCE, 0.0);
        defaults.put(Attribute.MOVEMENT_SPEED, 0.1);
        defaults.put(Attribute.FLYING_SPEED, 0.02);
        defaults.put(Attribute.ATTACK_DAMAGE, 1.0);
        defaults.put(Attribute.ATTACK_KNOCKBACK, 0.0);
        defaults.put(Attribute.ATTACK_SPEED, 4.0);
        defaults.put(Attribute.ARMOR, 0.0);
        defaults.put(Attribute.ARMOR_TOUGHNESS, 0.0);
        defaults.put(Attribute.FALL_DAMAGE_MULTIPLIER, 1.0);
        defaults.put(Attribute.LUCK, 0.0);
        defaults.put(Attribute.MAX_ABSORPTION, 0.0);
        defaults.put(Attribute.SAFE_FALL_DISTANCE, 3.0);
        defaults.put(Attribute.SCALE, 1.0);
        defaults.put(Attribute.STEP_HEIGHT, 0.6);
        defaults.put(Attribute.GRAVITY, 0.08);
        defaults.put(Attribute.JUMP_STRENGTH, 0.42);
        defaults.put(Attribute.BURNING_TIME, 1.0);
        defaults.put(Attribute.CAMERA_DISTANCE, 0.0);
        defaults.put(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0);
        defaults.put(Attribute.MOVEMENT_EFFICIENCY, 0.0);
        defaults.put(Attribute.OXYGEN_BONUS, 0.0);
        defaults.put(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.0);
        defaults.put(Attribute.BLOCK_INTERACTION_RANGE, 4.5);
        defaults.put(Attribute.ENTITY_INTERACTION_RANGE, 3.0);
        defaults.put(Attribute.BLOCK_BREAK_SPEED, 1.0);
        defaults.put(Attribute.MINING_EFFICIENCY, 0.0);
        defaults.put(Attribute.SNEAKING_SPEED, 0.3);
        defaults.put(Attribute.SUBMERGED_MINING_SPEED, 0.2);
        defaults.put(Attribute.SWEEPING_DAMAGE_RATIO, 0.0);
        return defaults;
    }

    private record PlayerWorldState(
        GameMode gameMode,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] extra,
        int heldSlot,
        ItemStack[] enderChest,
        Collection<PotionEffect> effects,
        Map<Attribute, AttributeState> attributes,
        double health,
        double absorption,
        int food,
        float saturation,
        float exhaustion,
        float exp,
        int level,
        int totalExp,
        boolean allowFlight,
        boolean flying,
        float flySpeed,
        float walkSpeed,
        boolean sneaking,
        boolean sprinting,
        int fireTicks,
        int freezeTicks,
        float fallDistance,
        int remainingAir,
        int maximumAir,
        int arrowsInBody,
        int noDamageTicks,
        int maximumNoDamageTicks,
        int portalCooldown,
        Location respawnLocation
    ) {
        static PlayerWorldState capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            Map<Attribute, AttributeState> attributes = new HashMap<>();
            for (Attribute attribute : allAttributes()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    attributes.put(attribute, AttributeState.capture(instance));
                }
            }
            return new PlayerWorldState(
                player.getGameMode(),
                cloneItems(inventory.getStorageContents()),
                cloneItems(inventory.getArmorContents()),
                cloneItems(inventory.getExtraContents()),
                inventory.getHeldItemSlot(),
                cloneItems(player.getEnderChest().getContents()),
                List.copyOf(player.getActivePotionEffects()),
                attributes,
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFlySpeed(),
                player.getWalkSpeed(),
                player.isSneaking(),
                player.isSprinting(),
                player.getFireTicks(),
                player.getFreezeTicks(),
                player.getFallDistance(),
                player.getRemainingAir(),
                player.getMaximumAir(),
                player.getArrowsInBody(),
                player.getNoDamageTicks(),
                player.getMaximumNoDamageTicks(),
                player.getPortalCooldown(),
                player.getRespawnLocation() == null ? null : player.getRespawnLocation().clone()
            );
        }

        void apply(Player player) {
            PlayerInventory playerInventory = player.getInventory();
            playerInventory.setStorageContents(cloneItems(inventory));
            playerInventory.setArmorContents(cloneItems(armor));
            playerInventory.setExtraContents(cloneItems(extra));
            playerInventory.setHeldItemSlot(heldSlot);
            player.getEnderChest().setContents(cloneItems(enderChest));

            player.clearActivePotionEffects();
            player.addPotionEffects(effects);
            applyAttributes(player);

            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setFlySpeed(flySpeed);
            player.setWalkSpeed(walkSpeed);
            player.setSneaking(sneaking);
            player.setSprinting(sprinting);

            player.setMaximumAir(maximumAir);
            player.setRemainingAir(remainingAir);
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(Math.min(health, maxHealth == null ? 20.0 : maxHealth.getValue()));
            player.setAbsorptionAmount(absorption);
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setExp(exp);
            player.setLevel(level);
            player.setTotalExperience(totalExp);
            player.setFireTicks(fireTicks);
            player.setFreezeTicks(freezeTicks);
            player.setFallDistance(fallDistance);
            player.setArrowsInBody(arrowsInBody);
            player.setMaximumNoDamageTicks(maximumNoDamageTicks);
            player.setNoDamageTicks(noDamageTicks);
            player.setPortalCooldown(portalCooldown);
            player.setRespawnLocation(respawnLocation == null ? player.getWorld().getSpawnLocation() : respawnLocation.clone(), true);
        }

        private void applyAttributes(Player player) {
            for (Attribute attribute : allAttributes()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                    instance.removeModifier(modifier);
                }
                AttributeState state = attributes.get(attribute);
                if (state == null) {
                    instance.setBaseValue(defaultBaseValue(attribute, instance));
                    continue;
                }
                instance.setBaseValue(state.baseValue());
                for (AttributeModifier modifier : state.modifiers()) {
                    instance.addModifier(modifier);
                }
            }
        }
    }

    private record AttributeState(double baseValue, Collection<AttributeModifier> modifiers) {
        static AttributeState capture(AttributeInstance instance) {
            return new AttributeState(instance.getBaseValue(), List.copyOf(instance.getModifiers()));
        }
    }

    @SuppressWarnings("deprecation")
    private static Iterable<Attribute> allAttributes() {
        return Bukkit.getRegistry(Attribute.class);
    }

    @SuppressWarnings("deprecation")
    private static double defaultBaseValue(Attribute attribute, AttributeInstance instance) {
        return VANILLA_PLAYER_ATTRIBUTES.getOrDefault(attribute, instance.getDefaultValue());
    }
}
