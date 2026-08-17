package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collection;
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
import org.bukkit.NamespacedKey;
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
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GamePlayer extends GameEntity {
    private static final int DEFAULT_EFFECT_DURATION_TICKS = 30 * 20;
    private static final int SPECTATOR_COMPASS_SLOT = 0;
    private static final NamespacedKey SPECTATOR_COMPASS_KEY = new NamespacedKey("donutgame", "spectator_compass");

    private final PlayerManager playerManager;
    private final PlayerState state = new PlayerState();
    private boolean spectator;
    private GameLocation respawnLocation;
    private GameTimer respawnTimer;

    GamePlayer(PlayerManager playerManager, GameWorld world, UUID uuid, GameLocation respawnLocation) {
        super(world, uuid, EntityType.PLAYER);
        this.playerManager = playerManager;
        this.respawnLocation = respawnLocation;
        Player player = player();
        if (player != null) state.capture(player);
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
        enterSpectator(location, true);
    }

    public void setSpectator(boolean spectator) {
        if (spectator) setSpectator();
        else setNonSpectator();
    }

    public void setSpectator(boolean spectator, GameLocation location) {
        if (spectator) {
            setSpectator(location);
            return;
        }
        if (location != null) teleport(location);
        setNonSpectator();
    }

    public void setSpectatorForRound(GameLocation location) {
        enterSpectator(location, false);
    }

    public void setNonSpectator() {
        spectator = false;
        Player player = player();
        if (player == null) return;
        player.closeInventory();
        removeSpectatorCompass(player.getInventory());
        applyStoredState(player);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.playerListName(null);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        refreshAfterLeavingSpectator(player);
    }

    public void resetForRound(GameLocation location, GameMode gameMode) {
        cancelRespawn();
        spectator = false;
        state.reset(gameMode == null ? GameMode.SURVIVAL : gameMode);
        setRespawnLocation(location);
        Player player = player();
        if (player != null) {
            if (location != null) teleport(location);
            applyStoredState(player);
            resetTransientState(player);
            player.setNoDamageTicks(20);
        }
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void syncSpectatorState() {
        if (!spectator) return;
        Player player = player();
        if (player != null) applySpectatorPresentation(player);
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
        if (ticks == 0) {
            GameLocation location = locationSupplier.get();
            if (location != null) {
                setRespawnLocation(location);
                teleport(location);
            }
            setNonSpectator();
            resetRespawnVitals(player());
            return;
        }
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

    public void giveItem(ItemStack item) {
        if (item == null) return;
        playerManager.module().plugin().itemService().give(this, List.of(item));
    }

    public Map<Integer, ItemStack> addToStoredInventory(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return Map.of();
        captureLiveState();
        Inventory temporaryInventory = Bukkit.createInventory(null, 36);
        temporaryInventory.setStorageContents(cloneContents(state.inventory));
        Map<Integer, ItemStack> leftovers = temporaryInventory.addItem(items.stream()
            .filter(item -> item != null && item.getType() != Material.AIR)
            .map(item -> item.clone())
            .toArray(ItemStack[]::new));
        state.inventory = cloneContents(temporaryInventory.getStorageContents());
        applyIfActive();
        return leftovers;
    }

    public void captureLiveState() {
        Player player = player();
        if (player != null && !spectator) state.capture(player);
    }

    public void clearInventory() {
        clearItems();
    }

    public void clearItems() {
        state.inventory = new ItemStack[36];
        state.armor = new ItemStack[4];
        state.offhand = null;
        applyIfActive();
    }

    public void clearItems(ItemStack item) {
        if (item == null) return;
        state.inventory = clearMatchingItems(state.inventory, item);
        state.armor = clearMatchingItems(state.armor, item);
        if (state.offhand != null && state.offhand.isSimilar(item)) state.offhand = null;
        applyIfActive();
    }

    public int itemCount(ItemStack item) {
        if (item == null) return 0;
        int count = 0;
        for (ItemStack current : concat(state.inventory, state.armor, state.offhand)) {
            if (current != null && current.isSimilar(item)) count += current.getAmount();
        }
        return count;
    }

    public void setGameMode(GameMode gameMode) {
        state.gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        applyIfActive();
    }

    public GameMode gameMode() {
        return state.gameMode;
    }

    public void setFoodLevel(int foodLevel) {
        setHunger(foodLevel);
    }

    public void setHunger(int hunger) {
        state.foodLevel = hunger;
        applyIfActive();
    }

    public int hunger() {
        return state.foodLevel;
    }

    public void setSaturation(float saturation) {
        state.saturation = saturation;
        applyIfActive();
    }

    public float saturation() {
        return state.saturation;
    }

    public void clearExperience() {
        setLevel(0);
        setExp(0);
        setTotalExperience(0);
    }

    public void setLevel(int level) {
        state.level = level;
        applyIfActive();
    }

    public void setExp(float progress) {
        state.exp = progress;
        applyIfActive();
    }

    public void setTotalExperience(int xp) {
        state.totalExperience = xp;
        applyIfActive();
    }

    public int level() {
        return state.level;
    }

    public float exp() {
        return state.exp;
    }

    public int totalExperience() {
        return state.totalExperience;
    }

    public void setArrowsInBody(int arrows) {
        state.arrowsInBody = Math.max(0, arrows);
        if (spectator) {
            Player player = player();
            if (player != null) player.setArrowsInBody(0);
            return;
        }
        applyIfActive();
    }

    public int arrowsInBody() {
        return state.arrowsInBody;
    }

    public void clearArrowsInBody() {
        setArrowsInBody(0);
    }

    public void setVelocity(Vector velocity) {
        Player player = player();
        if (player != null) player.setVelocity(velocity == null ? new Vector() : velocity);
    }

    public Vector getVelocity() {
        Player player = player();
        return player == null ? new Vector() : player.getVelocity();
    }

    @Override
    public void setHealth(double health) {
        state.health = Math.max(0, health);
        applyIfActive();
    }

    @Override
    public void damage(double damage) {
        if (spectator) {
            state.health = Math.max(0, state.health - damage);
            return;
        }
        super.damage(damage);
        Player player = player();
        if (player != null) state.health = player.getHealth();
    }

    @Override
    public void heal() {
        state.health = maxHealth();
        applyIfActive();
    }

    @Override
    public void heal(double health) {
        state.health = Math.min(state.health + Math.max(0, health), maxHealth());
        applyIfActive();
    }

    @Override
    public void addEffect(PotionEffectType effect) {
        addEffect(effect, DEFAULT_EFFECT_DURATION_TICKS);
    }

    @Override
    public void addEffect(PotionEffectType effect, int ticks) {
        addEffect(effect, ticks, 0);
    }

    @Override
    public void addEffect(PotionEffectType effect, int ticks, int amplifier) {
        addEffect(effect, ticks, amplifier, false);
    }

    @Override
    public void addEffect(PotionEffectType effect, int ticks, int amplifier, boolean hideParticles) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null.");
        if (ticks < 0 && ticks != PotionEffect.INFINITE_DURATION) throw new IllegalArgumentException("Effect duration cannot be negative.");
        if (amplifier < 0) throw new IllegalArgumentException("Effect amplifier cannot be negative.");
        addEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    @Override
    public void addEffect(PotionEffect effect) {
        if (effect == null) return;
        state.effects.removeIf(current -> current.getType().equals(effect.getType()));
        state.effects.add(effect);
        applyIfActive();
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
        effect(effect, 30, 0, false);
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

    public void removeEffect(PotionEffectType effect) {
        state.effects.removeIf(current -> current.getType().equals(effect));
        applyIfActive();
    }

    public Collection<PotionEffect> getEffects() {
        return List.copyOf(state.effects);
    }

    @Override
    public void clearEffects() {
        state.effects.clear();
        applyIfActive();
    }

    @Override
    public void clearEffects(PotionEffectType effect) {
        removeEffect(effect);
    }

    @Override
    public void setAttributeBaseValue(Attribute attribute, double value) {
        setAttributeBase(attribute, value);
    }

    @Override
    public void setAttributeBase(Attribute attribute, double value) {
        state.attributes.computeIfAbsent(attribute, key -> new StoredAttribute(value, List.of())).base = value;
        applyIfActive();
    }

    @Override
    public Double getAttributeValue(Attribute attribute) {
        return getAttributeBase(attribute);
    }

    @Override
    public double getAttribute(Attribute attribute) {
        return getAttributeBase(attribute);
    }

    @Override
    public double getAttributeBase(Attribute attribute) {
        StoredAttribute stored = state.attributes.get(attribute);
        return stored == null ? 0.0 : stored.base;
    }

    @Override
    public void resetAttribute(Attribute attribute) {
        Player player = player();
        AttributeInstance instance = player == null ? null : player.getAttribute(attribute);
        double base = instance == null ? 0.0 : instance.getDefaultValue();
        state.attributes.put(attribute, new StoredAttribute(base, List.of()));
        applyIfActive();
    }

    @Override
    public void resetAttributeBase(Attribute attribute) {
        Player player = player();
        AttributeInstance instance = player == null ? null : player.getAttribute(attribute);
        double base = instance == null ? 0.0 : instance.getDefaultValue();
        state.attributes.computeIfAbsent(attribute, key -> new StoredAttribute(base, List.of())).base = base;
        applyIfActive();
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

    private void enterSpectator(GameLocation location, boolean captureLiveState) {
        Player player = player();
        if (captureLiveState && !spectator && player != null) state.capture(player);
        spectator = true;
        if (location != null) teleport(location);
        if (player != null) applySpectatorPresentation(player);
        playerManager.module().uiManager().refreshPlayerState();
    }

    private void applyIfActive() {
        Player player = player();
        if (player != null && !spectator) applyStoredState(player);
    }

    private void applyStoredState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(stripSpectatorCompass(cloneContents(state.inventory)));
        inventory.setArmorContents(stripSpectatorCompass(cloneContents(state.armor)));
        inventory.setItemInOffHand(stripSpectatorCompass(state.offhand == null ? null : state.offhand.clone()));
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : state.effects) player.addPotionEffect(effect);
        for (Map.Entry<Attribute, StoredAttribute> entry : state.attributes.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance == null) continue;
            instance.setBaseValue(entry.getValue().base);
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) instance.removeModifier(modifier);
            for (AttributeModifier modifier : entry.getValue().modifiers) instance.addModifier(modifier);
        }
        player.setFoodLevel(state.foodLevel);
        player.setSaturation(state.saturation);
        player.setLevel(state.level);
        player.setExp(state.exp);
        player.setTotalExperience(state.totalExperience);
        player.setGameMode(state.gameMode);
        player.setArrowsInBody(state.arrowsInBody);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(Math.max(0.0, state.health), maxHealth == null ? state.health : maxHealth.getValue()));
        player.updateInventory();
    }

    private void applySpectatorPresentation(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        normalizeSpectatorInventory(inventory);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setArrowsInBody(0);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setInvisible(true);
        player.playerListName(spectatorPlayerListName(player));
        player.setFallDistance(0);
        Bukkit.getScheduler().runTask(playerManager.module().plugin(), () -> {
            if (!spectator || !player.isOnline()) return;
            player.setAllowFlight(true);
            if (!player.isFlying()) player.setFlying(true);
            player.setArrowsInBody(0);
        });
    }

    private Component spectatorPlayerListName(Player player) {
        GameTeam team = team();
        Component name = Component.text(player.getName(), team == null ? NamedTextColor.WHITE : team.color())
            .decoration(TextDecoration.ITALIC, false);
        return team == null
            ? name
            : Component.text().append(team.prefix()).append(name).append(team.suffix()).build().decoration(TextDecoration.ITALIC, false);
    }

    private void resetRespawnVitals(Player player) {
        if (player == null) return;
        state.health = maxHealth();
        state.foodLevel = 20;
        state.saturation = 20;
        state.effects.clear();
        state.arrowsInBody = 0;
        applyStoredState(player);
        resetTransientState(player);
        player.setNoDamageTicks(20);
        refreshAfterLeavingSpectator(player);
    }

    private void refreshAfterLeavingSpectator(Player player) {
        playerManager.module().uiManager().refreshPlayerStateAfterTrackingReset();
        Bukkit.getScheduler().runTaskLater(playerManager.module().plugin(), () -> refreshClientView(player), 1L);
        Bukkit.getScheduler().runTaskLater(playerManager.module().plugin(), () -> refreshClientView(player), 5L);
    }

    private void refreshClientView(Player player) {
        if (spectator || player == null || !player.isOnline()) return;
        player.setInvisible(false);
        player.updateInventory();
        player.teleport(player.getLocation());
        playerManager.module().uiManager().refreshPlayerStateAfterTrackingReset();
    }

    private void resetTransientState(Player player) {
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setExhaustion(0);
    }

    private double maxHealth() {
        Player player = player();
        AttributeInstance instance = player == null ? null : player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
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

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) return null;
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone();
        return clone;
    }

    private static ItemStack[] clearMatchingItems(ItemStack[] contents, ItemStack item) {
        if (contents == null) return null;
        ItemStack[] clone = cloneContents(contents);
        for (int i = 0; i < clone.length; i++) {
            if (clone[i] != null && clone[i].isSimilar(item)) clone[i] = null;
        }
        return clone;
    }

    private static ItemStack[] concat(ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
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

    private static final class PlayerState {
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
        private int arrowsInBody;
        private GameMode gameMode = GameMode.SURVIVAL;
        private final List<PotionEffect> effects = new ArrayList<>();

        private void capture(Player player) {
            inventory = stripSpectatorCompass(cloneContents(player.getInventory().getStorageContents()));
            armor = stripSpectatorCompass(cloneContents(player.getInventory().getArmorContents()));
            ItemStack currentOffhand = player.getInventory().getItemInOffHand();
            offhand = stripSpectatorCompass(currentOffhand == null ? null : currentOffhand.clone());
            health = Math.max(1.0, player.getHealth());
            foodLevel = player.getFoodLevel();
            saturation = player.getSaturation();
            level = player.getLevel();
            exp = player.getExp();
            totalExperience = player.getTotalExperience();
            arrowsInBody = player.getArrowsInBody();
            gameMode = player.getGameMode();
            effects.clear();
            effects.addAll(player.getActivePotionEffects());
            attributes.clear();
            for (Attribute attribute : Registry.ATTRIBUTE) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) attributes.put(attribute, new StoredAttribute(instance.getBaseValue(), List.copyOf(instance.getModifiers())));
            }
        }

        private void reset(GameMode gameMode) {
            inventory = new ItemStack[36];
            armor = new ItemStack[4];
            offhand = null;
            health = 20;
            foodLevel = 20;
            saturation = 20;
            level = 0;
            exp = 0;
            totalExperience = 0;
            arrowsInBody = 0;
            this.gameMode = gameMode;
            effects.clear();
            attributes.clear();
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
