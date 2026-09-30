package com.donutsforlife11.donutgame.internal.player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.player.PlayerAttributeDefaults;

public class PlayerWorldStateService implements Listener {
    private static final String SPECTATOR_MARKER_KEY = "spectator_session";

    private final Donutgame plugin;
    private final Map<UUID, Map<UUID, PlayerWorldState>> statesByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> isolatedWorldIds = ConcurrentHashMap.newKeySet();

    public PlayerWorldStateService(Donutgame plugin) {
        this.plugin = plugin;
    }

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
        isolatedWorldIds.remove(worldId);
        for (Map<UUID, PlayerWorldState> states : statesByPlayer.values()) {
            states.remove(worldId);
        }
        statesByPlayer.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void markIsolatedWorld(World world) {
        if (world != null) {
            isolatedWorldIds.add(world.getUID());
        }
    }

    public void cleanupRuntimeState() {
        statesByPlayer.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasDonutgameSpectatorMarker(player)) {
                clearLeakedSpectatorOverlay(player);
            }
        }
    }

    private void save(Player player, World world) {
        if (player == null || world == null) {
            return;
        }
        if (hasDonutgameSpectatorMarker(player)) {
            clearLeakedSpectatorOverlay(player);
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
        if (state != null) {
            state.apply(player, spectatorMarkerKey());
            player.updateInventory();
            return;
        }
        if (hasDonutgameSpectatorMarker(player)) {
            clearLeakedSpectatorOverlay(player);
            player.updateInventory();
            return;
        }
        if (isolatedWorldIds.contains(world.getUID())) {
            applyEmptyGameState(player);
            player.updateInventory();
            return;
        }
        player.getPersistentDataContainer().remove(spectatorMarkerKey());
    }

    private void applyEmptyGameState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[1]);
        inventory.setHeldItemSlot(0);
        player.getEnderChest().clear();

        player.clearActivePotionEffects();
        resetAttributes(player);

        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCanPickupItems(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.05f);
        player.setWalkSpeed(0.2f);
        player.setSneaking(false);
        player.setSprinting(false);

        player.setMaximumAir(300);
        player.setRemainingAir(player.getMaximumAir());
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setAbsorptionAmount(0.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0f);
        player.setArrowsInBody(0);
        player.setMaximumNoDamageTicks(20);
        player.setNoDamageTicks(0);
        player.setPortalCooldown(0);
        player.setRespawnLocation(null, true);
        player.getPersistentDataContainer().remove(spectatorMarkerKey());
    }

    private void clearLeakedSpectatorOverlay(Player player) {
        PlayerAttributeDefaults.repairInvalidCameraDistance(player);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCanPickupItems(true);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE);
        player.setFlying(false);
        player.setFallDistance(0.0f);
        player.getPersistentDataContainer().remove(spectatorMarkerKey());
    }

    private boolean hasDonutgameSpectatorMarker(Player player) {
        return player.getPersistentDataContainer().has(spectatorMarkerKey());
    }

    private NamespacedKey spectatorMarkerKey() {
        return new NamespacedKey(plugin, SPECTATOR_MARKER_KEY);
    }

    private static void resetAttributes(Player player) {
        for (Attribute attribute : allAttributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            PlayerAttributeDefaults.clearModifiers(instance);
            PlayerAttributeDefaults.restoreVanillaBase(instance);
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = items[i] == null ? null : items[i].clone();
        }
        return clone;
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
        boolean invulnerable,
        boolean invisible,
        boolean canPickupItems,
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
                player.isInvulnerable(),
                player.isInvisible(),
                player.getCanPickupItems(),
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

        void apply(Player player, NamespacedKey spectatorMarkerKey) {
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
            player.setInvulnerable(invulnerable);
            player.setInvisible(invisible);
            player.setCanPickupItems(canPickupItems);
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
            player.setRespawnLocation(respawnLocation == null ? null : respawnLocation.clone(), true);
            player.getPersistentDataContainer().remove(spectatorMarkerKey);
        }

        private void applyAttributes(Player player) {
            for (Attribute attribute : allAttributes()) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                PlayerAttributeDefaults.clearModifiers(instance);
                AttributeState state = attributes.get(attribute);
                if (state == null) {
                    PlayerAttributeDefaults.restoreVanillaBase(instance);
                    continue;
                }
                if (PlayerAttributeDefaults.isInvalidCameraDistance(attribute, state.baseValue())) {
                    PlayerAttributeDefaults.restoreVanillaBase(instance);
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
            double baseValue = PlayerAttributeDefaults.snapshotBaseValue(instance);
            return new AttributeState(baseValue, List.copyOf(instance.getModifiers()));
        }
    }

    @SuppressWarnings("deprecation")
    private static Iterable<Attribute> allAttributes() {
        return Bukkit.getRegistry(Attribute.class);
    }
}
