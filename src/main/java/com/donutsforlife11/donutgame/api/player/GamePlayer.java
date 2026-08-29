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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
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
    private final PlayerSnapshot snapshot = new PlayerSnapshot();
    private final PlayerSnapshot returnSnapshot = new PlayerSnapshot();
    private boolean spectator;
    private GameLocation respawnLocation;
    private GameTimer respawnTimer;

    GamePlayer(PlayerManager playerManager, GameWorld world, UUID uuid, GameLocation respawnLocation) {
        super(world, uuid, EntityType.PLAYER);
        this.playerManager = playerManager;
        this.respawnLocation = respawnLocation;
        Player player = player();
        if (player != null) {
            snapshot.capture(player);
            returnSnapshot.capture(player);
        }
    }

    @Override
    public Player bukkitEntity() {
        return Bukkit.getPlayer(uuid());
    }

    public Player player() {
        return bukkitEntity();
    }

    public Player bukkitPlayer() {
        return player();
    }

    public void setSpectator() {
        setSpectator(locationOrSpawn());
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
        snapshot.apply(player);
        resetTransientState(player);
        playerManager.module().uiManager().refreshPlayerStateAfterTrackingReset(this);
    }

    public void restoreBeforeGameState() {
        cancelRespawn();
        spectator = false;
        Player player = player();
        if (player == null) return;
        player.closeInventory();
        returnSnapshot.applyReturn(player);
        resetTransientState(player);
        playerManager.module().uiManager().refreshPlayerStateAfterTrackingReset(this);
    }

    public void restoreBeforeDisconnectSave() {
        Player player = player();
        if (player == null) return;
        player.closeInventory();
        returnSnapshot.applyReturn(player);
        resetTransientState(player);
    }

    public void resetForRound(GameLocation location, GameMode gameMode) {
        cancelRespawn();
        spectator = false;
        snapshot.reset(gameMode == null ? GameMode.SURVIVAL : gameMode);
        setSpawnPoint(location);
        Player player = player();
        if (player != null) {
            if (location != null) teleport(location);
            snapshot.apply(player);
            resetTransientState(player);
            player.setNoDamageTicks(20);
        }
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void syncSpectatorState() {
        Player player = player();
        if (player != null && spectator) applySpectatorMode(player);
    }

    public void syncSpectatorInventory() {
        syncSpectatorState();
    }

    public void syncStateAfterTrackingReset() {
        Player player = player();
        if (player == null) return;
        if (spectator) {
            applySpectatorMode(player);
        } else {
            snapshot.apply(player);
            resetTransientState(player);
        }
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
                setSpawnPoint(location);
                teleport(location);
            }
            setNonSpectator();
            resetRespawnVitals();
            return;
        }
        setSpectator();
        respawnTimer = playerManager.module().timeManager().newTimer(ticks)
            .onFinish(timer -> {
                respawnTimer = null;
                if (!playerManager.isRegistered(this) || player() == null) return;
                GameLocation location = locationSupplier.get();
                if (location != null) {
                    setSpawnPoint(location);
                    teleport(location);
                }
                setNonSpectator();
                resetRespawnVitals();
            })
            .start();
    }

    public void cancelRespawn() {
        if (respawnTimer != null) {
            respawnTimer.cancel();
            respawnTimer = null;
        }
    }

    public void setSpawnPoint(GameLocation location) {
        respawnLocation = location;
        Player player = player();
        if (player != null && location != null) {
            player.setRespawnLocation(location.toBukkit(world().bukkitWorld()), true);
        }
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

    public String name() {
        Player player = player();
        return player == null ? uuid().toString() : player.getName();
    }

    public boolean isOnline() {
        return player() != null;
    }

    public GameLocation spawnPoint() {
        return respawnLocation;
    }

    public GameTimer respawnTimer() {
        return respawnTimer;
    }

    public void giveItem(ItemStack item) {
        if (item != null) playerManager.module().plugin().itemService().give(this, List.of(item));
    }

    public Map<Integer, ItemStack> addToStoredInventory(Collection<ItemStack> items) {
        captureLiveState();
        Map<Integer, ItemStack> leftovers = snapshot.addItems(items);
        applySnapshotIfPlaying();
        return leftovers;
    }

    public void captureLiveState() {
        Player player = player();
        if (player != null && !spectator) snapshot.capture(player);
    }

    public void clearItems() {
        captureLiveState();
        snapshot.inventory = new ItemStack[36];
        snapshot.armor = new ItemStack[4];
        snapshot.offhand = null;
        applySnapshotIfPlaying();
    }

    public void clearItems(ItemStack item) {
        if (item == null) return;
        captureLiveState();
        snapshot.inventory = clearMatchingItems(snapshot.inventory, item);
        snapshot.armor = clearMatchingItems(snapshot.armor, item);
        if (snapshot.offhand != null && snapshot.offhand.isSimilar(item)) snapshot.offhand = null;
        applySnapshotIfPlaying();
    }

    public int itemCount(ItemStack item) {
        if (item == null) return 0;
        captureLiveState();
        int count = 0;
        for (ItemStack current : concat(snapshot.inventory, snapshot.armor, snapshot.offhand)) {
            if (current != null && current.isSimilar(item)) count += current.getAmount();
        }
        return count;
    }

    public void setGameMode(GameMode gameMode) {
        captureLiveState();
        snapshot.gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        applySnapshotIfPlaying();
    }

    public GameMode gameMode() {
        captureLiveState();
        return snapshot.gameMode;
    }

    public void setHunger(int hunger) {
        captureLiveState();
        snapshot.foodLevel = Math.max(0, Math.min(20, hunger));
        applySnapshotIfPlaying();
    }

    public int hunger() {
        captureLiveState();
        return snapshot.foodLevel;
    }

    public void setSaturation(float saturation) {
        captureLiveState();
        snapshot.saturation = Math.max(0, saturation);
        applySnapshotIfPlaying();
    }

    public float saturation() {
        captureLiveState();
        return snapshot.saturation;
    }

    public void clearExperience() {
        setLevel(0);
        setExp(0);
        setTotalExperience(0);
    }

    public void setLevel(int level) {
        captureLiveState();
        snapshot.level = Math.max(0, level);
        applySnapshotIfPlaying();
    }

    public void setExp(float progress) {
        captureLiveState();
        snapshot.exp = Math.max(0, Math.min(1, progress));
        applySnapshotIfPlaying();
    }

    public void setTotalExperience(int xp) {
        captureLiveState();
        snapshot.totalExperience = Math.max(0, xp);
        applySnapshotIfPlaying();
    }

    public int level() {
        captureLiveState();
        return snapshot.level;
    }

    public float exp() {
        captureLiveState();
        return snapshot.exp;
    }

    public int totalExperience() {
        captureLiveState();
        return snapshot.totalExperience;
    }

    public void setArrowsInBody(int arrows) {
        captureLiveState();
        snapshot.arrowsInBody = Math.max(0, arrows);
        applySnapshotIfPlaying();
    }

    public int arrowsInBody() {
        captureLiveState();
        return snapshot.arrowsInBody;
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
    public boolean teleport(GameLocation location) {
        Player player = player();
        return player != null && location != null && player.teleport(location.toBukkit(world().bukkitWorld()));
    }

    @Override
    public void setHealth(double health) {
        captureLiveState();
        snapshot.health = Math.max(0, health);
        applySnapshotIfPlaying();
    }

    @Override
    public void damage(double damage) {
        if (spectator) return;
        Player player = player();
        if (player != null) {
            player.damage(damage);
            snapshot.health = player.getHealth();
        }
    }

    @Override
    public void heal() {
        setHealth(maxHealth());
    }

    @Override
    public void heal(double health) {
        setHealth(Math.min(snapshot.health + Math.max(0, health), maxHealth()));
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
        addEffect(new PotionEffect(effect, ticks, amplifier, false, !hideParticles, !hideParticles));
    }

    @Override
    public void addEffect(PotionEffect effect) {
        if (effect == null) return;
        captureLiveState();
        snapshot.effects.removeIf(current -> current.getType().equals(effect.getType()));
        snapshot.effects.add(effect);
        applySnapshotIfPlaying();
    }

    public void removeEffect(PotionEffectType effect) {
        captureLiveState();
        snapshot.effects.removeIf(current -> current.getType().equals(effect));
        applySnapshotIfPlaying();
    }

    public Collection<PotionEffect> getEffects() {
        captureLiveState();
        return List.copyOf(snapshot.effects);
    }

    @Override
    public void clearEffects() {
        captureLiveState();
        snapshot.effects.clear();
        applySnapshotIfPlaying();
    }

    @Override
    public void clearEffects(PotionEffectType effect) {
        removeEffect(effect);
    }

    @Override
    public void setAttributeBase(Attribute attribute, double value) {
        captureLiveState();
        snapshot.attributeBases.put(attribute, value);
        if (!spectator && player() != null) super.setAttributeBase(attribute, value);
    }

    @Override
    public double getAttributeBase(Attribute attribute) {
        captureLiveState();
        return snapshot.attributeBases.getOrDefault(attribute, super.getAttributeBase(attribute));
    }

    @Override
    public double getAttribute(Attribute attribute) {
        return getAttributeBase(attribute);
    }

    @Override
    public void resetAttribute(Attribute attribute) {
        snapshot.attributeBases.remove(attribute);
        if (!spectator && player() != null) super.resetAttribute(attribute);
    }

    @Override
    public void resetAttributeBase(Attribute attribute) {
        resetAttribute(attribute);
    }

    public GameTeam team() {
        return playerManager.module().teamManager().getPlayerTeam(this);
    }

    public void setSpectatablePlayers(Collection<GamePlayer> players) {
        playerManager.setSpectatablePlayers(this, players);
    }

    public void setSpectatablePlayers(Supplier<? extends Collection<GamePlayer>> players) {
        playerManager.setSpectatablePlayers(this, players);
    }

    public void setSpectatableTeams(Collection<GameTeam> teams) {
        playerManager.setSpectatableTeams(this, teams);
    }

    public void setSpectatableTeams(Supplier<? extends Collection<GameTeam>> teams) {
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
        if (player != null && captureLiveState && !spectator) snapshot.capture(player);
        spectator = true;
        if (location != null) teleport(location);
        if (player != null) applySpectatorMode(player);
        playerManager.module().uiManager().refreshPlayerStateAfterTrackingReset(this);
    }

    private void applySpectatorMode(Player player) {
        player.closeInventory();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        inventory.setItem(SPECTATOR_COMPASS_SLOT, spectatorCompass());
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setArrowsInBody(0);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCollidable(true);
        player.setCanPickupItems(false);
        player.playerListName(spectatorPlayerListName(player));
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.updateInventory();
    }

    private Component spectatorPlayerListName(Player player) {
        GameTeam team = team();
        Component name = Component.text(player.getName(), team == null ? NamedTextColor.GRAY : team.color())
            .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return team == null ? name : Component.empty().append(team.prefix()).append(name).append(team.suffix());
    }

    private void applySnapshotIfPlaying() {
        Player player = player();
        if (player != null && !spectator) snapshot.apply(player);
    }

    private void resetRespawnVitals() {
        snapshot.health = maxHealth();
        snapshot.foodLevel = 20;
        snapshot.saturation = 20;
        snapshot.effects.clear();
        snapshot.arrowsInBody = 0;
        applySnapshotIfPlaying();
        Player player = player();
        if (player != null) {
            resetTransientState(player);
            player.setNoDamageTicks(20);
        }
    }

    private GameLocation locationOrSpawn() {
        Player player = player();
        if (player != null) return GameLocation.fromBukkit(player.getLocation());
        return respawnLocation;
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

    private static final class PlayerSnapshot {
        private ItemStack[] inventory = new ItemStack[36];
        private ItemStack[] armor = new ItemStack[4];
        private ItemStack offhand;
        private final Map<Attribute, Double> attributeBases = new LinkedHashMap<>();
        private double health = 20;
        private int foodLevel = 20;
        private float saturation = 20;
        private int level;
        private float exp;
        private int totalExperience;
        private int arrowsInBody;
        private GameMode gameMode = GameMode.SURVIVAL;
        private boolean invulnerable;
        private boolean invisible;
        private boolean collidable = true;
        private boolean canPickupItems = true;
        private boolean allowFlight;
        private boolean flying;
        private Component playerListName;
        private Scoreboard scoreboard;
        private Location respawnLocation;
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
            gameMode = player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode();
            invulnerable = player.isInvulnerable();
            invisible = player.isInvisible();
            collidable = player.isCollidable();
            canPickupItems = player.getCanPickupItems();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            playerListName = player.playerListName();
            scoreboard = player.getScoreboard();
            respawnLocation = player.getRespawnLocation();
            effects.clear();
            effects.addAll(player.getActivePotionEffects());
            attributeBases.clear();
            for (Attribute attribute : Registry.ATTRIBUTE) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) attributeBases.put(attribute, instance.getBaseValue());
            }
        }

        private void apply(Player player) {
            PlayerInventory playerInventory = player.getInventory();
            playerInventory.setStorageContents(stripSpectatorCompass(cloneContents(inventory)));
            playerInventory.setArmorContents(stripSpectatorCompass(cloneContents(armor)));
            playerInventory.setItemInOffHand(stripSpectatorCompass(offhand == null ? null : offhand.clone()));
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            for (PotionEffect effect : effects) player.addPotionEffect(effect);
            for (Map.Entry<Attribute, Double> entry : attributeBases.entrySet()) {
                AttributeInstance instance = player.getAttribute(entry.getKey());
                if (instance == null) continue;
                instance.setBaseValue(entry.getValue());
            }
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(totalExperience);
            player.setGameMode(gameMode);
            player.setArrowsInBody(arrowsInBody);
            player.setInvulnerable(invulnerable);
            player.setInvisible(invisible);
            player.setCollidable(collidable);
            player.setCanPickupItems(canPickupItems);
            player.playerListName(playerListName);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double cap = maxHealth == null ? health : maxHealth.getValue();
            player.setHealth(Math.min(Math.max(1.0, health), cap));
            player.updateInventory();
        }

        private void applyReturn(Player player) {
            apply(player);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }
            player.setRespawnLocation(respawnLocation, true);
        }

        private void reset(GameMode gameMode) {
            inventory = new ItemStack[36];
            armor = new ItemStack[4];
            offhand = null;
            attributeBases.clear();
            health = 20;
            foodLevel = 20;
            saturation = 20;
            level = 0;
            exp = 0;
            totalExperience = 0;
            arrowsInBody = 0;
            this.gameMode = gameMode;
            invulnerable = false;
            invisible = false;
            collidable = true;
            canPickupItems = true;
            allowFlight = gameMode == GameMode.CREATIVE;
            flying = false;
            playerListName = null;
            effects.clear();
        }

        private Map<Integer, ItemStack> addItems(Collection<ItemStack> items) {
            Map<Integer, ItemStack> leftovers = new LinkedHashMap<>();
            if (items == null) return leftovers;
            int itemIndex = 0;
            for (ItemStack item : items) {
                ItemStack remaining = addItem(item);
                if (remaining != null && remaining.getAmount() > 0) leftovers.put(itemIndex, remaining);
                itemIndex++;
            }
            return leftovers;
        }

        private ItemStack addItem(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) return null;
            ItemStack remaining = item.clone();
            for (ItemStack current : inventory) {
                if (current == null || !current.isSimilar(remaining)) continue;
                int transfer = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
                if (transfer <= 0) continue;
                current.setAmount(current.getAmount() + transfer);
                remaining.setAmount(remaining.getAmount() - transfer);
                if (remaining.getAmount() <= 0) return null;
            }
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null) continue;
                int transfer = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                inventory[i] = remaining.clone();
                inventory[i].setAmount(transfer);
                remaining.setAmount(remaining.getAmount() - transfer);
                if (remaining.getAmount() <= 0) return null;
            }
            return remaining;
        }
    }
}
