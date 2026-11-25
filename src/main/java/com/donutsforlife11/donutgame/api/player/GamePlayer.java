package com.donutsforlife11.donutgame.api.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.profile.PlayerProfile;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.internal.game.GameScope;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.GameItemComponents;
import com.donutsforlife11.donutgame.api.item.GameItemType;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GamePlayer implements GameEntityBase {
    private final GameModule module;
    private final long generation;
    private final UUID uuid;
    private final PlayerSnapshot playingState = PlayerSnapshot.empty();
    private SpectatorSession spectatorSession;
    private PlayerState state;
    private long attachedWorldGeneration;
    private String name;
    private Location lastLocation;
    private GameLocation spawnPoint;
    private long spawnPointGeneration;
    private GameTimer respawnTimer;
    private GameLocation pendingDeathSpectatorLocation;
    private GameLocation pendingVanillaRespawnLocation;
    private int pendingPostRespawnTimerTicks;
    private Supplier<GameLocation> pendingPostRespawnTimerLocation;
    private Supplier<Collection<GamePlayer>> spectatablePlayers;
    private Supplier<Collection<GameTeam>> spectatableTeams = List::of;
    private Scoreboard previousScoreboard;
    private PlayerProfile originalSkin;
    public void setSkin(PlayerProfile skin) {
        Player player = attachedPlayer();
        if(player == null) return;
        if(originalSkin == null) originalSkin = player.getPlayerProfile().clone();
        var changed = player.getPlayerProfile().clone();
        changed.clearProperties();
        changed.setProperties(skin.getProperties());
        player.setPlayerProfile(changed);
    }


    GamePlayer(GameModule module, UUID uuid) {
        this.module = module;
        this.generation = module.handleGeneration();
        this.uuid = uuid;
        this.state = PlayerState.OFFLINE;
        this.spectatablePlayers = () -> module.playerManager().getPlayers();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name == null ? uuid.toString() : name;
    }

    public PlayerProfile playerProfile() {
        Player player = bukkitPlayer();
        return player == null ? null : player.getPlayerProfile().clone();
    }

    Player bukkitPlayer() {
        if (!module.isHandleGenerationActive(generation)) {
            return null;
        }
        return playerInCurrentWorld();
    }

    Player playerInCurrentWorld() {
        if (module.playerManager() == null || module.playerManager().getPlayer(uuid) != this) {
            return null;
        }
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isConnected() && player.getWorld() == GameWorldHandle.bukkitWorld(world()) ? player : null;
    }

    Entity bukkitEntity() {
        return attachedPlayer();
    }

    @Override
    public GameLocation getLocation() {
        Player player = attachedPlayer();
        if (player != null) {
            return GameLocationHandle.create(world(), player.getLocation());
        }
        return lastGameLocation();
    }

    public GameModule module() {
        return module;
    }

    public GameWorld world() {
        return module().world();
    }

    public boolean isOnline() {
        return state == PlayerState.ONLINE && bukkitPlayer() != null;
    }

    public void setSpectator(boolean spectator) {
        setSpectator(spectator, locationOrSpawn());
    }

    public void setSpectator(boolean spectator, GameLocation location) {
        GameScope.requireActive(module);
        GameScope.requirePlayer(module, this);
        if (location != null) {
            GameScope.requireLocation(world(), location);
        }
        if (spectator) {
            enterSpectator(location);
            return;
        }

        // Leaving spectator mode is intentionally idempotent, but it is never a
        // no-op. A player can be logically non-spectating while stale Bukkit
        // projection bits (flight, invisibility, invulnerability, etc.) remain
        // from an interrupted transition. Always reconcile the physical player
        // back to the authoritative playing snapshot before continuing.
        exitSpectator(location);
    }

    public boolean isSpectator() {
        return spectatorSession != null || pendingDeathSpectatorLocation != null;
    }

    public void respawn() {
        respawn(0, () -> spawnPoint());
    }

    public void respawn(GameLocation location) {
        if (location != null) {
            GameScope.requireLocation(world(), location);
        }
        respawn(0, () -> location);
    }

    public void respawn(int ticks, Supplier<GameLocation> location) {
        GameScope.requireActive(module);
        GameScope.requirePlayer(module, this);
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks cannot be negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        cancelRespawn();
        Player player = attachedPlayer();
        if (player != null && player.isDead()) {
            if (ticks > 0) {
                if (pendingDeathSpectatorLocation == null) {
                    pendingDeathSpectatorLocation = locationOrSpawn();
                }
                pendingVanillaRespawnLocation = pendingDeathSpectatorLocation;
            } else {
                pendingVanillaRespawnLocation = locationOrSpawn();
            }
            pendingPostRespawnTimerTicks = ticks;
            pendingPostRespawnTimerLocation = location;
            return;
        }
        if (ticks == 0) {
            respawnNow(location);
            return;
        }
        startRespawnTimer(ticks, location);
    }

    private void startRespawnTimer(int ticks, Supplier<GameLocation> location) {
        setSpectator(true);
        respawnTimer = module().timeManager().newTimer(ticks)
            .onTick(timer -> showRespawnActionbar(timer.remainingTicks()))
            .onFinish(timer -> {
                respawnTimer = null;
                module().uiManager().actionbar(this, Component.empty());

                respawnNow(location);
            })
            .start();
    }

    public void cancelRespawn() {
        if (respawnTimer != null) {
            respawnTimer.cancel();
            respawnTimer = null;
        }
        pendingVanillaRespawnLocation = null;
        pendingPostRespawnTimerTicks = 0;
        pendingPostRespawnTimerLocation = null;
    }

    public void setSpawnPoint(GameLocation location) {
        spawnPoint = checkedRespawnLocation(location);
        spawnPointGeneration = GameWorldHandle.generation(world());
    }
    public void clearSpawnPoint() {
        setSpawnPoint(world().getSpawnLocation());
    }

    public GameLocation spawnPoint() {
        if (spawnPoint != null && spawnPointGeneration == GameWorldHandle.generation(world())) {
            return spawnPoint;
        }
        return defaultSpawnPoint();
    }

    public GameTimer respawnTimer() {
        return respawnTimer;
    }

    public void setSpectatablePlayers(Collection<GamePlayer> players) {
        List<GamePlayer> snapshot = players == null ? List.of() : List.copyOf(players);
        setSpectatablePlayers(() -> snapshot);
    }

    public void setSpectatablePlayers(Supplier<Collection<GamePlayer>> players) {
        spectatablePlayers = players == null ? List::of : players;
    }

    public Collection<GamePlayer> spectatablePlayers() {
        Collection<GamePlayer> players = spectatablePlayers.get();
        return players == null ? List.of() : players.stream()
            .filter(player -> player.module() == module && module.playerManager().getPlayer(player.uuid()) == player)
            .toList();
    }

    public void setSpectatableTeams(Collection<GameTeam> teams) {
        List<GameTeam> snapshot = teams == null ? List.of() : List.copyOf(teams);
        setSpectatableTeams(() -> snapshot);
    }

    public void setSpectatableTeams(Supplier<Collection<GameTeam>> teams) {
        spectatableTeams = teams == null ? List::of : teams;
    }

    public Collection<GameTeam> spectatableTeams() {
        Collection<GameTeam> teams = spectatableTeams.get();
        return teams == null ? List.of() : teams.stream().filter(module.teamManager().getTeams()::contains).toList();
    }

    public GameTeam getTeam() {
        return module().teamManager().getPlayerTeam(this);
    }

    void captureExternalScoreboard(Player player) {
        if (player != null && previousScoreboard == null) {
            previousScoreboard = player.getScoreboard();
        }
    }

    void restoreExternalScoreboard(Player player) {
        if (player != null && previousScoreboard != null) {
            player.setScoreboard(previousScoreboard);
        }
        previousScoreboard = null;
    }

    public void setGameMode(GameMode gameMode) {
        GameMode nextMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        if (nextMode == GameMode.SPECTATOR) {
            setSpectator(true);
            return;
        }
        playingState.gameMode = nextMode;
        playingState.allowFlight = playingState.gameMode == GameMode.CREATIVE;
        if (!playingState.allowFlight) {
            playingState.flying = false;
        }
        applyVisiblePlayer(player -> player.setGameMode(playingState.gameMode));
    }

    public GameMode getGameMode() {
        return playingState.gameMode;
    }

    public void setFoodLevel(int hunger) {
        playingState.foodLevel = Math.max(0, Math.min(20, hunger));
        applyVisiblePlayer(player -> player.setFoodLevel(playingState.foodLevel));
    }

    public void setSprinting(boolean sprinting) {
        applyVisiblePlayer(player -> player.setSprinting(sprinting));
    }

    public int getFoodLevel() {
        return playingState.foodLevel;
    }

    public void setSaturation(float saturation) {
        playingState.saturation = Math.max(0, saturation);
        applyVisiblePlayer(player -> player.setSaturation(playingState.saturation));
    }

    public float getSaturation() {
        return playingState.saturation;
    }

    public void setArrowsInBody(int arrows) {
        playingState.arrowsInBody = Math.max(0, arrows);
        applyVisiblePlayer(player -> player.setArrowsInBody(playingState.arrowsInBody));
    }

    public int getArrowsInBody() {
        return playingState.arrowsInBody;
    }

    public void clearExperience() {
        playingState.level = 0;
        playingState.exp = 0.0f;
        playingState.totalExperience = 0;
        applyVisiblePlayer(player -> {
            player.setLevel(0);
            player.setExp(0.0f);
            player.setTotalExperience(0);
        });
    }

    public void setLevel(int level) {
        playingState.level = Math.max(0, level);
        applyVisiblePlayer(player -> player.setLevel(playingState.level));
    }

    public int getLevel() {
        return playingState.level;
    }

    public void setExp(float exp) {
        playingState.exp = Math.max(0.0f, Math.min(1.0f, exp));
        applyVisiblePlayer(player -> player.setExp(playingState.exp));
    }

    public float getExp() {
        return playingState.exp;
    }

    public void reset() {
        cancelRespawn();
        playingState.reset(GameMode.SURVIVAL);
        restorePlayingState(attachedPlayer());
    }

    private GameLocation lastGameLocation() {
        return lastLocation == null || lastLocation.getWorld() != GameWorldHandle.bukkitWorld(world())
            ? null : GameLocationHandle.create(world(), lastLocation);
    }

    public void giveItem(GameItemType item) {
        if (item == null) {
            return;
        }
        giveItem(item.create());
    }

    public void giveItem(GameItem item) {
        if (item == null) {
            return;
        }
        ItemStack stack = GameItemComponents.normalize(this, item.copyBukkitItem());
        Player player = visiblePlayer();
        if (player != null) {
            player.getInventory().addItem(stack);
            player.updateInventory();
            capturePlayingState(player);
            return;
        }
        playingState.addItem(stack);
    }

    public List<GameItem> inventory() {
        captureVisibleInventory();
        List<GameItem> items = new ArrayList<>();
        for (ItemStack item : PlayerSnapshot.cloneItems(playingState.inventory)) {
            items.add(GameItem.from(item));
        }
        return Collections.unmodifiableList(items);
    }

    public List<GameItem> hotbar() {
        captureVisibleInventory();
        List<GameItem> items = new ArrayList<>();
        for (int index = 0; index < Math.min(9, playingState.inventory.length); index++) {
            items.add(GameItem.from(playingState.inventory[index]));
        }
        return Collections.unmodifiableList(items);
    }

    public void setHotbarItem(int slot, GameItem item) {
        if (slot < 0 || slot >= 9) {
            throw new IllegalArgumentException("hotbar slot must be between 0 and 8");
        }
        setInventoryItem(slot, item);
    }

    public GameItem getHotbarItem(int slot) {
        if (slot < 0 || slot >= 9) {
            throw new IllegalArgumentException("hotbar slot must be between 0 and 8");
        }
        return getInventoryItem(slot);
    }

    /** Slots 0-8 hotbar, 9-35 storage, 36-39 feet/legs/chest/head, 40 offhand. */
    public void setInventoryItem(int slot, GameItem item) {
        checkInventorySlot(slot);
        if (slot >= 36) {
            setItem(inventoryEquipmentSlot(slot), item);
            return;
        }
        ItemStack stack = item == null ? null : GameItemComponents.normalize(this, item.copyBukkitItem());
        playingState.inventory[slot] = stack == null ? null : stack.clone();
        applyVisiblePlayer(player -> {
            player.getInventory().setItem(slot, stack == null ? null : stack.clone());
            player.updateInventory();
        });
    }

    public GameItem getInventoryItem(int slot) {
        checkInventorySlot(slot);
        if (slot >= 36) return getItem(inventoryEquipmentSlot(slot));
        Player player = visiblePlayer();
        if (player != null) {
            ItemStack item = player.getInventory().getItem(slot);
            playingState.inventory[slot] = item == null ? null : item.clone();
        }
        return GameItem.from(playingState.inventory[slot]);
    }

    private void captureVisibleInventory() {
        Player player = visiblePlayer();
        if (player != null) playingState.inventory = PlayerSnapshot.cloneItems(player.getInventory().getStorageContents());
    }

    private static void checkInventorySlot(int slot) {
        if (slot < 0 || slot > 40) throw new IllegalArgumentException("Inventory slot must be between 0 and 40.");
    }

    private static EquipmentSlot inventoryEquipmentSlot(int slot) {
        return switch (slot) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            case 40 -> EquipmentSlot.OFF_HAND;
            default -> throw new IllegalArgumentException("Not an equipment slot: " + slot);
        };
    }

    @Override
    public void setHealth(double health) {
        playingState.health = Math.max(0.0, Math.min(health, maxHealth()));
        applyVisiblePlayer(player -> {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double maximum = maxHealth == null ? 20.0 : maxHealth.getValue();
            player.setHealth(Math.min(Math.max(0.0, playingState.health), maximum));
        });
    }

    @Override
    public void heal() {
        setHealth(maxHealth());
    }

    @Override
    public void heal(float amount) {
        setHealth(getHealth() + Math.max(0.0f, amount));
    }

    @Override
    public void damage(double amount) {
        if (isSpectator()) {
            return;
        }
        GameEntityBase.super.damage(amount);
        captureVisiblePlayingState();
    }

    @Override
    public double getHealth() {
        Player player = visiblePlayer();
        return player == null ? playingState.health : player.getHealth();
    }

    @Override
    public void kill() {
        setHealth(0.0f);
    }

    @Override
    public void setVelocity(Vector velocity) {
        Player player = attachedPlayer();
        if (player != null) {
            player.setVelocity(velocity == null ? new Vector() : velocity.clone());
        }
    }

    @Override
    public Vector getVelocity() {
        Player player = attachedPlayer();
        return player == null ? new Vector() : player.getVelocity().clone();
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        playingState.invulnerable = invulnerable;
        applyVisiblePlayer(player -> player.setInvulnerable(invulnerable));
    }

    @Override
    public boolean isInvulnerable() {
        return playingState.invulnerable;
    }

    @Override
    public void setInvisible(boolean invisible) {
        playingState.invisible = invisible;
        applyVisiblePlayer(player -> player.setInvisible(invisible));
    }

    @Override
    public boolean isInvisible() {
        return playingState.invisible;
    }

    @Override
    public void setFireTicks(int ticks) {
        playingState.fireTicks = Math.max(0, ticks);
        applyVisiblePlayer(player -> player.setFireTicks(playingState.fireTicks));
    }

    @Override
    public int getFireTicks() {
        return playingState.fireTicks;
    }

    @Override
    public boolean addPotionEffect(PotionEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect cannot be null");
        }
        playingState.effects.removeIf(current -> current.getType().equals(effect.getType()));
        playingState.effects.add(effect);
        Player player = visiblePlayer();
        if (player == null) {
            return true;
        }
        player.removePotionEffect(effect.getType());
        return player.addPotionEffect(effect);
    }

    @Override
    public void clearActivePotionEffects() {
        playingState.effects.clear();
        applyVisiblePlayer(Player::clearActivePotionEffects);
    }

    @Override
    public void removePotionEffect(PotionEffectType effect) {
        if (effect == null) {
            return;
        }
        playingState.effects.removeIf(current -> current.getType().equals(effect));
        applyVisiblePlayer(player -> player.removePotionEffect(effect));
    }

    @Override
    public boolean hasPotionEffect(PotionEffectType effect) {
        return effect != null && playingState.effects.stream().anyMatch(current -> current.getType().equals(effect));
    }

    @Override
    public List<PotionEffect> getActivePotionEffects() {
        return List.copyOf(playingState.effects);
    }

    @Override
    public void setAttributeBase(Attribute attribute, double value) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        playingState.attributeBases.put(attribute, value);
        applyVisibleAttribute(attribute, instance -> instance.setBaseValue(value));
    }

    @Override
    public void resetAttributeBase(Attribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        playingState.attributeBases.remove(attribute);
        applyVisibleAttribute(attribute, PlayerAttributeDefaults::restoreVanillaBase);
    }

    @Override
    public double attributeBase(Attribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        Double value = playingState.attributeBases.get(attribute);
        if (value != null) {
            return value;
        }
        Player player = bukkitPlayer();
        AttributeInstance instance = player == null ? null : player.getAttribute(attribute);
        return instance == null ? 0.0 : PlayerAttributeDefaults.baseValue(instance);
    }

    @Override
    public void addModifier(Attribute attribute, AttributeModifier modifier) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        if (modifier == null) {
            throw new IllegalArgumentException("modifier cannot be null");
        }
        playingState.attributeModifiers.computeIfAbsent(attribute, ignored -> new ArrayList<>()).add(modifier);
        applyVisibleAttribute(attribute, instance -> instance.addModifier(modifier));
    }

    @Override
    public void removeModifier(Attribute attribute, AttributeModifier modifier) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        if (modifier == null) {
            return;
        }
        List<AttributeModifier> modifiers = playingState.attributeModifiers.get(attribute);
        if (modifiers != null) {
            modifiers.remove(modifier);
        }
        applyVisibleAttribute(attribute, instance -> instance.removeModifier(modifier));
    }

    @Override
    public double attributeModifierValue(Attribute attribute, AttributeModifier modifier) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        if (modifier == null) {
            throw new IllegalArgumentException("modifier cannot be null");
        }
        return playingState.attributeModifiers.getOrDefault(attribute, List.of()).contains(modifier) ? modifier.getAmount() : 0.0;
    }

    @Override
    public List<AttributeModifier> attributeModifiers(Attribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute cannot be null");
        }
        return List.copyOf(playingState.attributeModifiers.getOrDefault(attribute, List.of()));
    }

    @Override
    public double attributeValue(Attribute attribute) {
        Player player = visiblePlayer();
        AttributeInstance live = player == null ? null : player.getAttribute(attribute);
        return live != null ? live.getValue() : PlayerSnapshot.attributeValue(
            attributeBase(attribute), playingState.attributeModifiers.getOrDefault(attribute, List.of()));
    }

    @Override
    public void setItem(EquipmentSlot slot, GameItemType item) {
        setItem(slot, item == null ? null : item.create());
    }

    @Override
    public void setItem(EquipmentSlot slot, GameItem item) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        ItemStack stack = item == null ? null : GameItemComponents.normalize(this, item.copyBukkitItem());
        Player player = visiblePlayer();
        if (player != null) {
            playingState.heldItemSlot = player.getInventory().getHeldItemSlot();
            player.getInventory().setItem(slot, stack);
            player.updateInventory();
        }
        playingState.setItem(slot, stack);
    }

    @Override
    public GameItem getItem(EquipmentSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        Player player = visiblePlayer();
        return GameItem.from(player == null ? playingState.getItem(slot) : player.getInventory().getItem(slot));
    }

    @Override
    public void clearItems() {
        playingState.clearInventory();
        applyVisiblePlayer(player -> {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setExtraContents(new ItemStack[1]);
            player.updateInventory();
        });
    }

    @Override
    public void clearItems(GameItem item) {
        if (item == null) {
            clearItems();
            return;
        }
        ItemStack bukkitItem = item.copyBukkitItem();
        clearItems(stack -> stack != null && stack.isSimilar(bukkitItem));
    }

    void reattach(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        PlayerAttributeDefaults.repairInvalidCameraDistance(player);
        com.donutsforlife11.donutgame.internal.game.EntityCollisions.set(world(), player, world().playerCollisionsEnabled());
        name = player.getName();
        state = PlayerState.ONLINE;
        long currentGeneration = GameWorldHandle.generation(world());
        boolean replaced = attachedWorldGeneration != currentGeneration;
        attachedWorldGeneration = currentGeneration;
        lastLocation = player.getLocation().clone();
        if (spawnPoint == null || replaced) {
            setSpawnPoint(defaultSpawnPoint());
        }
        if (spectatorSession != null) {
            spectatorSession.apply(player);
        } else if (pendingDeathSpectatorLocation != null && !player.isDead()) {
            // Reconnect can replace the vanilla post-respawn callback.
            if (replaced) {
                pendingDeathSpectatorLocation = spawnPoint;
            }
            enterPostDeathSpectator(spawnPoint());
        } else {
            applyPlayingStateIfVisible();
        }
    }

    void startFreshSession(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        cancelRespawn();
        pendingDeathSpectatorLocation = null;
        closeSpectatorSession(player);
        resetPlayingStateForGame();
        com.donutsforlife11.donutgame.internal.game.EntityCollisions.set(world(), player, world().playerCollisionsEnabled());
        name = player.getName();
        state = PlayerState.ONLINE;
        attachedWorldGeneration = GameWorldHandle.generation(world());
        lastLocation = player.getLocation().clone();
        setSpawnPoint(defaultSpawnPoint());
        applyPlayingStateIfVisible();
    }

    void markOffline(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }

        boolean logicalSpectator = isSpectator();
        if (!logicalSpectator) {
            capturePlayingState(player);
        }

        lastLocation = player.getLocation().clone();
        // Detach first. From this point onward, game/module code may still run
        // during PlayerQuitEvent, but attachedPlayer() will return null and no
        // spectator overlay can be written back onto the disconnecting entity.
        state = PlayerState.OFFLINE;

        // Spectatorship is intentionally preserved in memory across relogs.
        // Only remove its Bukkit projection so Minecraft cannot serialize
        // spectator flight/inventory state or any legacy projection bits.
        if (logicalSpectator) {
            if (spectatorSession != null) {
                spectatorSession.detach(player);
            }
            if (!player.isDead()) {
                playingState.apply(player);
            }
            SpectatorProjection.applyNonSpectatorOverlay(player, playingState);
            assertPhysicalPlayingOverlay(player, "disconnect");
            player.updateInventory();
        }
    }

    void enterPostDeathSpectator(GameLocation location) {
        GameLocation spectatorLocation = pendingDeathSpectatorLocation == null ? location : pendingDeathSpectatorLocation;
        pendingDeathSpectatorLocation = null;
        enterSpectator(spectatorLocation);
        if (pendingPostRespawnTimerLocation != null) {
            int ticks = pendingPostRespawnTimerTicks;
            Supplier<GameLocation> respawnLocation = pendingPostRespawnTimerLocation;
            pendingPostRespawnTimerTicks = 0;
            pendingPostRespawnTimerLocation = null;
            pendingVanillaRespawnLocation = null;
            if (ticks == 0) {
                respawnNow(respawnLocation);
            } else {
                startRespawnTimer(ticks, respawnLocation);
            }
            return;
        }
        GameLocation pendingLocation = pendingVanillaRespawnLocation;
        pendingVanillaRespawnLocation = null;
        if (pendingLocation != null) {
            respawnNow(pendingLocation);
        }
    }

    void rememberVanillaDeath() {
        playingState.ageTimedValues();
    }

    GameLocation postDeathRespawnLocation(GameLocation fallback) {
        if (pendingVanillaRespawnLocation != null) {
            return pendingVanillaRespawnLocation;
        }
        if (pendingDeathSpectatorLocation != null) {
            return pendingDeathSpectatorLocation;
        }
        return fallback;
    }

    void captureBeforeWorldSwap(Player player) {
        capturePlayingState(player);
        cancelRespawn();
    }

    void detachAfterWorldExit(Player player) {
        if(originalSkin != null && player != null) player.setPlayerProfile(originalSkin);
        originalSkin = null;
        cancelRespawn();
        pendingDeathSpectatorLocation = null;
        closeSpectatorSession(player);
        restoreExternalScoreboard(player);
        state = PlayerState.OFFLINE;
    }

    void prepareForWorldExit() {
        cancelRespawn();
        restorePlayingState(state == PlayerState.ONLINE ? playerInCurrentWorld() : null);
    }

    private GameLocation checkedRespawnLocation(GameLocation location) {
        if (location != null) {
            GameScope.requireLocation(world(), location);
        }
        return location;
    }

    private void respawnNow(GameLocation location) { respawnNow(() -> location); }

    private void respawnNow(Supplier<GameLocation> prepare) {
        // RODO: Reset before the game callback so its fresh effects and kit survive restoration.
        GameLocation location = playingState.prepareRespawn(maxHealth(), prepare);
        checkedRespawnLocation(location);
        Player player = attachedPlayer();
        if (player != null && player.isDead()) {
            pendingVanillaRespawnLocation = location == null ? spawnPoint() : location;
            return;
        }
        pendingDeathSpectatorLocation = null;
        GameLocation target = location == null ? spawnPoint() : location;
        if (target != null) {
            setSpawnPoint(target);
        }
        exitSpectator(target);
        player = attachedPlayer();
        if (player != null) {
            player.setNoDamageTicks(0);
        }
    }

    private void showRespawnActionbar(int remainingTicks) {
        int remainingSeconds = Math.max(0, (remainingTicks + 19) / 20);
        module().uiManager().actionbar(
            this,
            Component.text("Respawning in " + String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60), NamedTextColor.GREEN)
        );
    }

    private void captureVisiblePlayingState() {
        if (!isSpectator()) {
            Player player = attachedPlayer();
            if (player != null) {
                capturePlayingState(player);
            }
        } else {
            playingState.ageTimedValues();
        }
    }

    private void applyPlayingStateIfVisible() {
        Player player = attachedPlayer();
        if (player != null && !isSpectator()) {
            if (player.isDead()) {
                SpectatorProjection.applyNonSpectatorOverlay(player, playingState);
                player.updateInventory();
                return;
            }
            playingState.apply(player);
            player.updateInventory();
        }
    }

    private void clearItems(java.util.function.Predicate<ItemStack> matcher) {
        playingState.clearItems(matcher);
        applyVisiblePlayer(player -> {
            clearInventoryItems(player.getInventory().getStorageContents(), matcher, player.getInventory()::setStorageContents);
            clearInventoryItems(player.getInventory().getArmorContents(), matcher, player.getInventory()::setArmorContents);
            clearInventoryItems(player.getInventory().getExtraContents(), matcher, player.getInventory()::setExtraContents);
            player.updateInventory();
        });
    }

    private void enterSpectator(GameLocation location) {
        Player player = attachedPlayer();
        if (player != null && player.isDead()) {
            pendingDeathSpectatorLocation = location == null ? locationOrSpawn() : location;
            Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(Donutgame.class), () -> {
                Player current = attachedPlayer();
                if (module.acceptsLiveOperations() && pendingDeathSpectatorLocation != null && current != null && current.isDead()) current.spigot().respawn();
            });
            return;
        }
        pendingDeathSpectatorLocation = null;
        if (spectatorSession == null) {
            if (player != null) {
                capturePlayingState(player);
            }
            spectatorSession = SpectatorSession.open(this, player);

        } else if (player != null) {
            spectatorSession.apply(player);

        }
        if (player != null && location != null) {
            teleport(location);
        }
    }

    private void exitSpectator(GameLocation location) {
        Player player = attachedPlayer();
        restorePlayingState(player);
        if (player != null && location != null) {
            teleport(location);
        }
    }

    private void restorePlayingState(Player player) {
        pendingDeathSpectatorLocation = null;
        closeSpectatorSession(player);
        if (player != null) {
            if (!player.isDead()) {
                playingState.apply(player);
            } else {
                SpectatorProjection.applyNonSpectatorOverlay(player, playingState);
            }
            assertPhysicalPlayingOverlay(player, "playing state restore");
            player.updateInventory();
        }
    }

    private void closeSpectatorSession(Player player) {
        SpectatorSession session = spectatorSession;
        spectatorSession = null;
        if (session != null) {
            session.close(player);
        }
    }

    private void assertPhysicalPlayingOverlay(Player player, String transition) {
        if (player == null) {
            return;
        }
        repairPhysicalPlayingOverlay(player);
        if (!SpectatorProjection.matches(player, playingState)) {
            String differences = SpectatorProjection.describeMismatch(player, playingState);
            module.logError(
                "Spectator cleanup failed for " + player.getName() + " (" + uuid + ") during " + transition
                    + ". Remaining mismatch: " + differences
                    + "; physical={" + SpectatorProjection.describePhysical(player) + "}"
                    + "; expected={" + SpectatorProjection.describeExpected(playingState) + "}",
                new IllegalStateException("Spectator projection could not be fully cleared")
            );
            scheduleSpectatorOverlayRepair(transition);
        }
    }

    private void repairPhysicalPlayingOverlay(Player player) {
        if (!SpectatorProjection.matches(player, playingState)) {
            SpectatorProjection.applyNonSpectatorOverlay(player, playingState);
        }
    }

    private void resetPlayingStateForGame() {
        playingState.reset(GameMode.SURVIVAL);
        playingState.gameMode = GameMode.ADVENTURE;
        playingState.allowFlight = false;
        playingState.flying = false;
        playingState.restoreAliveDefaults(maxHealth());
    }

    private void capturePlayingState(Player player) {
        if (player == null) {
            return;
        }
        if (isSpectator()) {
            playingState.ageTimedValues();
            return;
        }
        playingState.capture(player);
    }

    private void scheduleSpectatorOverlayRepair(String transition) {
        Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(Donutgame.class), () -> {
            Player player = attachedPlayer();
            if (player == null || isSpectator()) {
                return;
            }
            SpectatorProjection.applyNonSpectatorOverlay(player, playingState);
            player.setNoDamageTicks(0);
            player.updateInventory();
            if (!SpectatorProjection.matches(player, playingState)) {
                module.logError(
                    "Spectator cleanup still has a physical mismatch for " + player.getName() + " (" + uuid + ") after deferred "
                        + transition + " repair. Remaining mismatch: " + SpectatorProjection.describeMismatch(player, playingState)
                        + "; physical={" + SpectatorProjection.describePhysical(player) + "}"
                        + "; expected={" + SpectatorProjection.describeExpected(playingState) + "}",
                    new IllegalStateException("Deferred spectator projection repair did not fully clear")
                );
            }
        });
    }

    private Player attachedPlayer() {
        return state == PlayerState.ONLINE ? bukkitPlayer() : null;
    }

    private Player visiblePlayer() {
        Player player = attachedPlayer();
        return player != null && !isSpectator() ? player : null;
    }

    private void applyVisiblePlayer(Consumer<Player> action) {
        Player player = visiblePlayer();
        if (player != null) {
            action.accept(player);
        }
    }

    private void applyVisibleAttribute(Attribute attribute, Consumer<AttributeInstance> action) {
        applyVisiblePlayer(player -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                action.accept(instance);
            }
        });
    }

    private void clearInventoryItems(
        ItemStack[] items,
        java.util.function.Predicate<ItemStack> matcher,
        Consumer<ItemStack[]> setter
    ) {
        for (int index = 0; index < items.length; index++) {
            if (matcher.test(items[index])) {
                items[index] = null;
            }
        }
        setter.accept(items);
    }

    private GameLocation locationOrSpawn() {
        GameLocation location = getLocation();
        return location == null ? spawnPoint() : location;
    }

    private GameLocation defaultSpawnPoint() {
        GameWorld world = world();
        GameLocation point = world.getPoint("spawn");
        return point == null ? world.getSpawnLocation() : point;
    }

    private double maxHealth() {
        Player player = bukkitPlayer();
        AttributeInstance instance = player == null ? null : player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }

}
