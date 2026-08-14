package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GamePlayer extends GameEntity {
    private static final int DEFAULT_EFFECT_SECONDS = 30;
    private static final int SPECTATOR_COMPASS_SLOT = 0;
    private static final NamespacedKey SPECTATOR_COMPASS_KEY = new NamespacedKey("donutgame", "spectator_compass");
    private final PlayerManager playerManager;
    private boolean spectator;
    private GameLocation respawnLocation;
    private GameTimer respawnTimer;
    private StoredState storedState;

    GamePlayer(PlayerManager playerManager, GameWorld world, UUID uuid, GameLocation respawnLocation) {
        super(world, uuid, EntityType.PLAYER);
        this.playerManager = playerManager;
        this.respawnLocation = respawnLocation;
    }

    @Override
    public Player bukkitEntity() {
        return Bukkit.getPlayer(uuid());
    }

    public Player player() {
        return bukkitEntity();
    }

    public Player bukkitPlayer() {
        return bukkitEntity();
    }

    public void setSpectator() {
        setSpectator(location());
    }

    public void setSpectator(GameLocation location) {
        setSpectator(location, true);
    }

    public void setSpectator(boolean spectator) {
        if (spectator) setSpectator();
        else setNonSpectator();
    }

    public void setSpectator(boolean spectator, GameLocation location) {
        if (spectator) setSpectator(location);
        else {
            if (location != null) teleport(location);
            setNonSpectator();
        }
    }

    public void setSpectatorForRound(GameLocation location) {
        setSpectator(location, false);
    }

    private void setSpectator(GameLocation location, boolean saveState) {
        spectator = true;
        Player player = player();
        if (saveState && player != null) storeStateIfNeeded(player);
        if (!saveState) storedState = null;
        if (location != null) teleport(location);
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        syncSpectatorState(player);
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void setNonSpectator() {
        setNonSpectator(true);
    }

    private void setNonSpectator(boolean restoreState) {
        spectator = false;
        Player player = player();
        if (!restoreState) storedState = null;
        if (player == null) return;
        player.closeInventory();
        removeSpectatorCompass(player.getInventory());
        if (restoreState) restoreStateIfNeeded(player);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.playerListName(null);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void resetForRound(GameLocation location, GameMode gameMode) {
        cancelRespawn();
        setNonSpectator(false);
        setRespawnLocation(location);
        Player player = player();
        if (player == null) return;
        player.closeInventory();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : Math.min(20.0, maxHealth.getValue()));
        if (gameMode != null) player.setGameMode(gameMode);
        if (location != null) teleport(location);
        player.setVelocity(new org.bukkit.util.Vector());
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setNoDamageTicks(20);
        player.setRemainingAir(player.getMaximumAir());
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void syncSpectatorState() {
        if (!spectator) return;
        Player player = player();
        if (player != null) syncSpectatorState(player);
    }

    public void addEffect(PotionEffectType effect, int ticks, int amplifier) {
        addEffect(effect, ticks, amplifier, false);
    }

    public void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null.");
        if (ticks < 0 && ticks != PotionEffect.INFINITE_DURATION) throw new IllegalArgumentException("Effect duration cannot be negative.");
        if (amplifier < 0) throw new IllegalArgumentException("Effect amplifier cannot be negative.");
        addEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    public void addVanillaEffect(PotionEffectType effect) {
        effect(effect);
    }

    public void addVanillaEffect(PotionEffectType effect, int seconds) {
        effect(effect, seconds);
    }

    public void addVanillaEffect(PotionEffectType effect, int seconds, int amplifier) {
        effect(effect, seconds, amplifier);
    }

    public void addVanillaEffect(PotionEffectType effect, int seconds, int amplifier, boolean hideParticles) {
        effect(effect, seconds, amplifier, hideParticles);
    }

    public void effect(PotionEffectType effect) {
        effect(effect, DEFAULT_EFFECT_SECONDS, 0, false);
    }

    public void effect(PotionEffectType effect, int seconds) {
        effect(effect, seconds, 0, false);
    }

    public void effect(PotionEffectType effect, int seconds, int amplifier) {
        effect(effect, seconds, amplifier, false);
    }

    public void effect(PotionEffectType effect, int seconds, int amplifier, boolean hideParticles) {
        int durationTicks = seconds == PotionEffect.INFINITE_DURATION ? PotionEffect.INFINITE_DURATION : seconds * 20;
        addEffect(effect, durationTicks, amplifier, hideParticles);
    }

    @Override
    public void addEffect(PotionEffect effect) {
        if (effect == null) return;
        if (spectator && storedState != null) {
            storedState.effects.removeIf(current -> current.getType().equals(effect.getType()));
            storedState.effects.add(effect);
            return;
        }
        Player player = player();
        if (player != null) player.addPotionEffect(effect);
    }

    public void removeEffect(PotionEffectType effect) {
        if (spectator && storedState != null) {
            storedState.effects.removeIf(current -> current.getType().equals(effect));
            return;
        }
        Player player = player();
        if (player != null) player.removePotionEffect(effect);
    }

    public Collection<PotionEffect> getEffects() {
        if (spectator && storedState != null) return List.copyOf(storedState.effects);
        Player player = player();
        return player == null ? Collections.emptyList() : player.getActivePotionEffects();
    }

    public void clearEffects() {
        if (spectator && storedState != null) {
            storedState.effects.clear();
            return;
        }
        Player player = player();
        if (player == null) return;
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    public void respawn() {
        respawn(0, () -> respawnLocation);
    }

    public void respawn(GameLocation location) {
        respawn(0, location);
    }

    public void respawn(int ticks) {
        respawn(ticks, () -> respawnLocation);
    }

    public void respawn(int ticks, GameLocation location) {
        respawn(ticks, () -> location);
    }

    public void respawn(int ticks, Supplier<GameLocation> locationSupplier) {
        if (ticks < 0) throw new IllegalArgumentException("Respawn time cannot be negative.");
        cancelRespawn();
        if (spectator) syncSpectatorState();
        else setSpectator();
        respawnTimer = playerManager.module().timeManager().newTimer(ticks).onTick(20, timer -> {
            int remainingSeconds = Math.round(timer.getRemainingTicks() / 20.0f);
            playerManager.module().uiManager().actionbar(this, Component.text("Respawning in: ").append(Component.text(String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60), NamedTextColor.GREEN)));
        }).onFinish(timer -> {
            respawnTimer = null;
            if (!playerManager.isRegistered(this) || player() == null) return;
            GameLocation location = locationSupplier.get();
            if (location != null) {
                setRespawnLocation(location);
                teleport(location);
            }
            setNonSpectator();
            resetRespawnVitals(player());
        }).start();
    }

    public void cancelRespawn() {
        if (respawnTimer != null) {
            respawnTimer.cancel();
            respawnTimer = null;
        }
    }

    public void setRespawnLocation(GameLocation location) {
        setSpawnPoint(location);
    }

    public void setSpawnPoint(GameLocation location) {
        respawnLocation = location;
        Player player = player();
        if (player != null && location != null) player.setRespawnLocation(location.toBukkit(world().bukkitWorld()), true);
    }

    public void setSpawnPoint(double x, double y, double z) {
        setSpawnPoint(new GameLocation(x, y, z));
    }

    public void setSpawnPoint(double x, double y, double z, double pitch, double yaw) {
        setSpawnPoint(new GameLocation(x, y, z, pitch, yaw));
    }

    public Map<Integer, ItemStack> addToStoredInventory(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return Map.of();
        StoredState state = ensureStoredState();
        Inventory temporaryInventory = Bukkit.createInventory(null, 36);
        temporaryInventory.setContents(cloneContents(state.inventory));
        Map<Integer, ItemStack> leftovers = temporaryInventory.addItem(items.stream().map(item -> item.clone()).toArray(ItemStack[]::new));
        state.inventory = cloneContents(temporaryInventory.getContents());
        return leftovers;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public GamePlayer setSpectatable(boolean spectatable) {
        playerManager.setPlayerSpectatable(this, spectatable);
        return this;
    }

    public String getName() {
        return name();
    }

    public String name() {
        Player player = player();
        return player == null ? uuid().toString() : player.getName();
    }

    public void setGameMode(GameMode gameMode) {
        if (spectator && storedState != null) {
            storedState.gameMode = gameMode;
            return;
        }
        Player player = player();
        if (player != null) player.setGameMode(gameMode);
    }

    public GameMode gameMode() {
        if (spectator && storedState != null && storedState.gameMode != null) return storedState.gameMode;
        Player player = player();
        return player == null ? GameMode.SPECTATOR : player.getGameMode();
    }

    public boolean isOnline() {
        return player() != null;
    }

    public GameLocation respawnLocation() {
        return spawnPoint();
    }

    public GameLocation spawnPoint() {
        return respawnLocation;
    }

    public GameTimer respawnTimer() {
        return respawnTimer;
    }

    public void clearInventory() {
        clearItems();
    }

    public void giveItem(ItemStack item) {
        if (item == null) return;
        playerManager.module().plugin().itemService().give(this, List.of(item));
    }

    public void clearItems() {
        if (spectator && storedState != null) {
            storedState.inventory = new ItemStack[36];
            storedState.armor = new ItemStack[4];
            storedState.offhand = null;
            return;
        }
        Player player = player();
        if (player != null) player.getInventory().clear();
    }

    public void clearItems(ItemStack item) {
        if (item == null) return;
        if (spectator && storedState != null) {
            storedState.inventory = clearMatchingItems(storedState.inventory, item);
            storedState.armor = clearMatchingItems(storedState.armor, item);
            if (storedState.offhand != null && storedState.offhand.isSimilar(item)) storedState.offhand = null;
            return;
        }
        Player player = player();
        if (player == null) return;
        player.getInventory().removeItemAnySlot(item);
    }

    public int itemCount(ItemStack item) {
        if (item == null) return 0;
        int count = 0;
        ItemStack[] contents = spectator && storedState != null
            ? concat(storedState.inventory, storedState.armor, storedState.offhand)
            : player() == null ? new ItemStack[0] : player().getInventory().getContents();
        for (ItemStack current : contents) {
            if (current != null && current.isSimilar(item)) count += current.getAmount();
        }
        return count;
    }

    public void setFoodLevel(int foodLevel) {
        setHunger(foodLevel);
    }

    public void setHunger(int hunger) {
        if (spectator && storedState != null) {
            storedState.foodLevel = hunger;
            return;
        }
        Player player = player();
        if (player != null) player.setFoodLevel(hunger);
    }

    public int hunger() {
        if (spectator && storedState != null) return storedState.foodLevel;
        Player player = player();
        return player == null ? 20 : player.getFoodLevel();
    }

    public void setSaturation(float saturation) {
        if (spectator && storedState != null) {
            storedState.saturation = saturation;
            return;
        }
        Player player = player();
        if (player != null) player.setSaturation(saturation);
    }

    public float saturation() {
        if (spectator && storedState != null) return storedState.saturation;
        Player player = player();
        return player == null ? 0 : player.getSaturation();
    }

    public void clearExperience() {
        setLevel(0);
        setExp(0);
        setTotalExperience(0);
    }

    public void setLevel(int level) {
        if (spectator && storedState != null) {
            storedState.level = level;
            return;
        }
        Player player = player();
        if (player != null) player.setLevel(level);
    }

    public void setExp(float progress) {
        if (spectator && storedState != null) {
            storedState.exp = progress;
            return;
        }
        Player player = player();
        if (player != null) player.setExp(progress);
    }

    public void setTotalExperience(int xp) {
        if (spectator && storedState != null) {
            storedState.totalExperience = xp;
            return;
        }
        Player player = player();
        if (player != null) player.setTotalExperience(xp);
    }

    public int level() {
        if (spectator && storedState != null) return storedState.level;
        Player player = player();
        return player == null ? 0 : player.getLevel();
    }

    public float exp() {
        if (spectator && storedState != null) return storedState.exp;
        Player player = player();
        return player == null ? 0 : player.getExp();
    }

    public int totalExperience() {
        if (spectator && storedState != null) return storedState.totalExperience;
        Player player = player();
        return player == null ? 0 : player.getTotalExperience();
    }

    @Override
    public void setHealth(double health) {
        if (spectator && storedState != null) {
            storedState.health = Math.max(0, health);
            return;
        }
        super.setHealth(health);
    }

    @Override
    public void damage(double damage) {
        if (spectator && storedState != null) {
            storedState.health = Math.max(0, storedState.health - damage);
            return;
        }
        super.damage(damage);
    }

    @Override
    public void setAttributeBaseValue(Attribute attribute, double value) {
        if (spectator && storedState != null) {
            storedState.attributes.computeIfAbsent(attribute, key -> new StoredAttribute(value, List.of())).base = value;
            return;
        }
        super.setAttributeBaseValue(attribute, value);
    }

    @Override
    public Double getAttributeValue(Attribute attribute) {
        if (spectator && storedState != null) {
            StoredAttribute stored = storedState.attributes.get(attribute);
            return stored == null ? null : stored.base;
        }
        return super.getAttributeValue(attribute);
    }

    public GameTeam team() {
        return playerManager.module().teamManager().getPlayerTeam(this);
    }

    public void setSpectatablePlayers(Collection<GamePlayer> players) {
        playerManager.setSpectatablePlayers(this, players);
    }

    public void setSpectatableTeams(Collection<GameTeam> teams) {
        playerManager.setSpectatableTeams(this, teams);
    }

    public Collection<GamePlayer> spectatablePlayers() {
        return playerManager.spectatablePlayers(this);
    }

    public Collection<GameTeam> spectatableTeams() {
        return playerManager.spectatableTeams(this);
    }

    public Collection<GamePlayer> teammates() {
        GameTeam team = team();
        return team == null ? Set.of() : team.getMembers();
    }

    public GamePlayer closestTeammate(Predicate<GamePlayer> filter) {
        GamePlayer closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (GamePlayer teammate : teammates()) {
            if (teammate.uuid().equals(uuid()) || !filter.test(teammate) || teammate.player() == null) continue;
            double distance = teammate.location().distanceSquared(location());
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = teammate;
            }
        }
        return closest;
    }

    private StoredState ensureStoredState() {
        if (storedState != null) return storedState;
        Player player = player();
        storedState = player == null ? new StoredState() : StoredState.of(player);
        return storedState;
    }

    private void storeStateIfNeeded(Player player) {
        if (storedState == null) storedState = StoredState.of(player);
    }

    private void restoreStateIfNeeded(Player player) {
        if (storedState == null) return;
        player.getInventory().setStorageContents(stripSpectatorCompass(cloneContents(storedState.inventory)));
        player.getInventory().setArmorContents(stripSpectatorCompass(cloneContents(storedState.armor)));
        player.getInventory().setItemInOffHand(stripSpectatorCompass(storedState.offhand == null ? null : storedState.offhand.clone()));
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : storedState.effects) player.addPotionEffect(effect);
        for (Map.Entry<Attribute, StoredAttribute> entry : storedState.attributes.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance == null) continue;
            instance.setBaseValue(entry.getValue().base);
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) instance.removeModifier(modifier);
            for (AttributeModifier modifier : entry.getValue().modifiers) instance.addModifier(modifier);
        }
        player.setFoodLevel(storedState.foodLevel);
        player.setSaturation(storedState.saturation);
        player.setLevel(storedState.level);
        player.setExp(storedState.exp);
        player.setTotalExperience(storedState.totalExperience);
        if (storedState.gameMode != null) player.setGameMode(storedState.gameMode);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(storedState.health, maxHealth == null ? storedState.health : maxHealth.getValue()));
        storedState = null;
    }

    private void resetRespawnVitals(Player player) {
        if (player == null) return;
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
        player.setNoDamageTicks(20);
        player.setRemainingAir(player.getMaximumAir());
    }

    private void syncSpectatorState(Player player) {
        normalizeSpectatorInventory(player.getInventory());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setInvisible(true);
        player.playerListName(Component.text(player.getName(), NamedTextColor.GRAY));
        player.setFallDistance(0);
        Bukkit.getScheduler().runTask(playerManager.module().plugin(), () -> {
            if (!spectator || !player.isOnline()) return;
            player.setAllowFlight(true);
            if (!player.isFlying()) player.setFlying(true);
        });
    }

    private void normalizeSpectatorInventory(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        boolean storageDirty = false;
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (slot == SPECTATOR_COMPASS_SLOT) {
                if (!isSpectatorCompass(item)) {
                    storage[slot] = spectatorCompass();
                    storageDirty = true;
                }
                continue;
            }
            if (item != null) {
                storage[slot] = null;
                storageDirty = true;
            }
        }
        if (storageDirty) inventory.setStorageContents(storage);
        if (containsAnyItem(inventory.getArmorContents())) inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        if (inventory.getItemInOffHand() != null) inventory.setItemInOffHand(null);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) return null;
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone();
        return clone;
    }

    private ItemStack[] clearMatchingItems(ItemStack[] contents, ItemStack item) {
        if (contents == null) return null;
        ItemStack[] clone = cloneContents(contents);
        for (int i = 0; i < clone.length; i++) {
            if (clone[i] != null && clone[i].isSimilar(item)) clone[i] = null;
        }
        return clone;
    }

    private ItemStack[] concat(ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
        int inventoryLength = inventory == null ? 0 : inventory.length;
        int armorLength = armor == null ? 0 : armor.length;
        ItemStack[] combined = new ItemStack[inventoryLength + armorLength + 1];
        if (inventory != null) System.arraycopy(inventory, 0, combined, 0, inventory.length);
        if (armor != null) System.arraycopy(armor, 0, combined, inventoryLength, armor.length);
        combined[combined.length - 1] = offhand;
        return combined;
    }

    public static ItemStack spectatorCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Spectate", NamedTextColor.AQUA));
            meta.getPersistentDataContainer().set(SPECTATOR_COMPASS_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isSpectatorCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(SPECTATOR_COMPASS_KEY, PersistentDataType.BYTE);
        return marker != null && marker != 0;
    }

    public static void removeSpectatorCompass(PlayerInventory inventory) {
        if (inventory == null) return;
        inventory.setStorageContents(stripSpectatorCompass(inventory.getStorageContents()));
        inventory.setArmorContents(stripSpectatorCompass(inventory.getArmorContents()));
        inventory.setItemInOffHand(stripSpectatorCompass(inventory.getItemInOffHand()));
        inventory.setItemInMainHand(stripSpectatorCompass(inventory.getItemInMainHand()));
        ItemStack cursor = inventory.getItem(SPECTATOR_COMPASS_SLOT);
        if (isSpectatorCompass(cursor)) inventory.setItem(SPECTATOR_COMPASS_SLOT, null);
    }

    public static ItemStack[] stripSpectatorCompass(ItemStack[] contents) {
        if (contents == null) return null;
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = stripSpectatorCompass(contents[i] == null ? null : contents[i].clone());
        return clone;
    }

    public static ItemStack stripSpectatorCompass(ItemStack item) {
        return isSpectatorCompass(item) ? null : item;
    }

    private static boolean containsAnyItem(ItemStack[] contents) {
        if (contents == null) return false;
        for (ItemStack item : contents) if (item != null) return true;
        return false;
    }

    private static final class StoredState {
        private ItemStack[] inventory = new ItemStack[36];
        private ItemStack[] armor = new ItemStack[4];
        private ItemStack offhand;
        private final Map<Attribute, StoredAttribute> attributes = new LinkedHashMap<>();
        private double health = 20;
        private int foodLevel = 20;
        private float saturation = 5;
        private int level;
        private float exp;
        private int totalExperience;
        private GameMode gameMode = GameMode.SURVIVAL;
        private final List<PotionEffect> effects = new ArrayList<>();

        private static StoredState of(Player player) {
            StoredState state = new StoredState();
            state.inventory = stripSpectatorCompass(state.cloneContents(player.getInventory().getStorageContents()));
            state.armor = stripSpectatorCompass(state.cloneContents(player.getInventory().getArmorContents()));
            ItemStack offhand = player.getInventory().getItemInOffHand();
            state.offhand = stripSpectatorCompass(offhand == null ? null : offhand.clone());
            state.health = Math.max(1.0, player.getHealth());
            state.foodLevel = player.getFoodLevel();
            state.saturation = player.getSaturation();
            state.level = player.getLevel();
            state.exp = player.getExp();
            state.totalExperience = player.getTotalExperience();
            state.gameMode = player.getGameMode();
            state.effects.addAll(player.getActivePotionEffects());
            for (Attribute attribute : Registry.ATTRIBUTE) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) state.attributes.put(attribute, new StoredAttribute(instance.getBaseValue(), List.copyOf(instance.getModifiers())));
            }
            return state;
        }

        private ItemStack[] cloneContents(ItemStack[] contents) {
            if (contents == null) return null;
            ItemStack[] clone = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone();
            return clone;
        }
    }

    private static final class StoredAttribute {
        private double base;
        private final List<AttributeModifier> modifiers;

        private StoredAttribute(double base, List<AttributeModifier> modifiers) {
            this.base = base;
            this.modifiers = new ArrayList<>(modifiers);
        }
    }
}
