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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.item.GameItem;
import com.donutsforlife11.donutgame.api.item.ItemSpec;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GamePlayer implements GameEntityBase {
    private final GameModule module;
    private final UUID uuid;
    private final PlayerSnapshot playingState = PlayerSnapshot.empty();
    private SpectatorSession spectatorSession;
    private PlayerState state;
    private String lastWorldName;
    private Location lastLocation;
    private GameLocation spawnPoint;
    private GameTimer respawnTimer;
    private GameLocation pendingVanillaRespawnLocation;
    private int pendingPostRespawnTimerTicks;
    private Supplier<GameLocation> pendingPostRespawnTimerLocation;
    private Supplier<Collection<GamePlayer>> spectatablePlayers;
    private Supplier<Collection<GameTeam>> spectatableTeams = List::of;
    private Scoreboard previousScoreboard;

    GamePlayer(GameModule module, UUID uuid) {
        this.module = module;
        this.uuid = uuid;
        this.state = PlayerState.OFFLINE;
        this.spectatablePlayers = () -> module.playerManager().getPlayers();
    }

    public UUID uuid() {
        return uuid;
    }

    public Player bukkitPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public Entity bukkitEntity() {
        return bukkitPlayer();
    }

    public GameModule module() {
        return module;
    }

    public GameWorld world() {
        return module().world();
    }

    public PlayerState state() {
        return state;
    }

    public boolean isOnline() {
        return state == PlayerState.ONLINE && bukkitPlayer() != null;
    }

    public void setSpectator(boolean spectator) {
        setSpectator(spectator, locationOrSpawn());
    }

    public void setSpectator(boolean spectator, GameLocation location) {
        if (spectator) {
            enterSpectator(location);
        } else {
            exitSpectator(location);
        }
    }

    public boolean isSpectator() {
        return spectatorSession != null;
    }

    public void respawn() {
        respawn(0, () -> spawnPoint());
    }

    public void respawn(int ticks) {
        respawn(ticks, () -> spawnPoint());
    }

    public void respawn(GameLocation location) {
        respawn(0, location);
    }

    public void respawn(int ticks, GameLocation location) {
        respawn(ticks, () -> location);
    }

    public void respawn(int ticks, Supplier<GameLocation> location) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks cannot be negative");
        }
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        cancelRespawn();
        Player player = bukkitPlayer();
        if (player != null && player.isDead()) {
            pendingPostRespawnTimerTicks = ticks;
            pendingPostRespawnTimerLocation = location;
            pendingVanillaRespawnLocation = location.get();
            return;
        }
        if (ticks == 0) {
            respawnNow(location.get());
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

                respawnNow(location.get());
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
        spawnPoint = location;
    }
    public void clearSpawnPoint() {
        spawnPoint = world().worldSpawn();
    }

    public GameLocation spawnPoint() {
        if (spawnPoint != null) {
            return spawnPoint;
        }
        GameWorld world = world();
        return world == null ? null : world.worldSpawn();
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
        return players == null ? List.of() : List.copyOf(players);
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
        return teams == null ? List.of() : List.copyOf(teams);
    }

    public GameTeam getTeam() {
        return module().teamManager().getPlayerTeam(this);
    }

    public void captureExternalScoreboard(Player player) {
        if (player != null && previousScoreboard == null) {
            previousScoreboard = player.getScoreboard();
        }
    }

    public void restoreExternalScoreboard(Player player) {
        if (player != null && previousScoreboard != null) {
            player.setScoreboard(previousScoreboard);
        }
        previousScoreboard = null;
    }

    public void setGameMode(GameMode gameMode) {
        playingState.gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        applyVisiblePlayer(player -> player.setGameMode(playingState.gameMode));
    }

    public GameMode gameMode() {
        return playingState.gameMode;
    }

    public void setHunger(int hunger) {
        playingState.foodLevel = Math.max(0, Math.min(20, hunger));
        applyVisiblePlayer(player -> player.setFoodLevel(playingState.foodLevel));
    }

    public int hunger() {
        return playingState.foodLevel;
    }

    public void setSaturation(float saturation) {
        playingState.saturation = Math.max(0, saturation);
        applyVisiblePlayer(player -> player.setSaturation(playingState.saturation));
    }

    public float saturation() {
        return playingState.saturation;
    }

    public void setArrowsInBody(int arrows) {
        playingState.arrowsInBody = Math.max(0, arrows);
        applyVisiblePlayer(player -> player.setArrowsInBody(playingState.arrowsInBody));
    }

    public int arrowsInBody() {
        return playingState.arrowsInBody;
    }

    public void reset() {
        cancelRespawn();
        closeSpectatorSession();
        playingState.reset(GameMode.SURVIVAL);
        applyPlayingStateIfVisible();
    }

    public String lastWorldName() {
        return lastWorldName;
    }

    public Location lastLocation() {
        return lastLocation == null ? null : lastLocation.clone();
    }

    public void title(Component title) {
        module().uiManager().title(this, title);
    }

    public void subtitle(Component subtitle) {
        module().uiManager().subtitle(this, subtitle);
    }

    public void actionbar(Component actionbar) {
        module().uiManager().actionbar(this, actionbar);
    }

    public void chat(Component message) {
        module().uiManager().chat(this, message);
    }

    public void gameMessage(Component message) {
        module().uiManager().gameMessage(this, message);
    }

    public void give(ItemSpec item) {
        giveItem(item);
    }

    public void giveItem(ItemSpec item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        giveItem(item.createItem());
    }

    public void giveItem(GameItem item) {
        if (item == null) {
            return;
        }
        Player player = visiblePlayer();
        if (player != null) {
            player.getInventory().addItem(item.copyBukkitItem());
            player.updateInventory();
            playingState.capture(player);
            return;
        }
        playingState.addItem(item.copyBukkitItem());
    }

    public List<GameItem> inventory() {
        List<GameItem> items = new ArrayList<>();
        for (ItemStack item : PlayerSnapshot.cloneItems(playingState.inventory)) {
            items.add(GameItem.from(item));
        }
        return Collections.unmodifiableList(items);
    }

    public List<GameItem> hotbar() {
        List<GameItem> items = new ArrayList<>();
        for (int index = 0; index < Math.min(9, playingState.inventory.length); index++) {
            items.add(GameItem.from(playingState.inventory[index]));
        }
        return Collections.unmodifiableList(items);
    }

    @Override
    public void setHealth(float health) {
        playingState.health = Math.max(0.0, Math.min(health, maxHealth()));
        applyVisiblePlayer(player -> {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double maximum = maxHealth == null ? 20.0 : maxHealth.getValue();
            player.setHealth(Math.min(Math.max(0.0, playingState.health), maximum));
        });
    }

    @Override
    public void heal() {
        setHealth((float) maxHealth());
    }

    @Override
    public void heal(float amount) {
        setHealth((float) (playingState.health + Math.max(0.0f, amount)));
    }

    @Override
    public void damage(float amount) {
        if (isSpectator()) {
            return;
        }
        GameEntityBase.super.damage(amount);
        captureVisiblePlayingState();
    }

    @Override
    public void damage(float amount, DamageSource source) {
        if (isSpectator()) {
            return;
        }
        GameEntityBase.super.damage(amount, source);
        captureVisiblePlayingState();
    }

    @Override
    public float health() {
        return (float) playingState.health;
    }

    @Override
    public void kill() {
        setHealth(0.0f);
    }

    @Override
    public void setVelocity(Vector velocity) {
        Player player = bukkitPlayer();
        if (player != null) {
            player.setVelocity(velocity == null ? new Vector() : velocity.clone());
        }
    }

    @Override
    public Vector velocity() {
        Player player = bukkitPlayer();
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
    public int fireTicks() {
        return playingState.fireTicks;
    }

    @Override
    public void addEffect(PotionEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect cannot be null");
        }
        playingState.effects.removeIf(current -> current.getType().equals(effect.getType()));
        playingState.effects.add(effect);
        applyVisiblePlayer(player -> {
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(effect);
        });
    }

    @Override
    public void clearEffects() {
        playingState.effects.clear();
        applyVisiblePlayer(Player::clearActivePotionEffects);
    }

    @Override
    public void clearEffect(PotionEffectType effect) {
        if (effect == null) {
            return;
        }
        playingState.effects.removeIf(current -> current.getType().equals(effect));
        applyVisiblePlayer(player -> player.removePotionEffect(effect));
    }

    @Override
    public boolean hasEffect(PotionEffectType effect) {
        return effect != null && playingState.effects.stream().anyMatch(current -> current.getType().equals(effect));
    }

    @Override
    public List<PotionEffect> effects() {
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
        double value = attributeBase(attribute);
        for (AttributeModifier modifier : playingState.attributeModifiers.getOrDefault(attribute, List.of())) {
            String operation = modifier.getOperation().name();
            if ("ADD_NUMBER".equals(operation)) {
                value += modifier.getAmount();
            } else if ("ADD_SCALAR".equals(operation)) {
                value += attributeBase(attribute) * modifier.getAmount();
            } else if ("MULTIPLY_SCALAR_1".equals(operation)) {
                value *= 1.0 + modifier.getAmount();
            }
        }
        return value;
    }

    @Override
    public void setItem(EquipmentSlot slot, ItemSpec item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        setItem(slot, item.createItem());
    }

    @Override
    public void setItem(EquipmentSlot slot, GameItem item) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        playingState.setItem(slot, item == null ? null : item.copyBukkitItem());
        applyVisiblePlayer(player -> {
            setPlayerInventorySlot(player, slot, item == null ? null : item.copyBukkitItem());
            player.updateInventory();
        });
    }

    @Override
    public GameItem getItem(EquipmentSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        return GameItem.from(playingState.getItem(slot));
    }

    @Override
    public void clearItems() {
        playingState.inventory = new ItemStack[36];
        playingState.armor = new ItemStack[4];
        playingState.extra = new ItemStack[1];
        applyVisiblePlayer(player -> {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setExtraContents(new ItemStack[1]);
            player.updateInventory();
        });
    }

    @Override
    public void clearItems(ItemSpec item) {
        if (item == null) {
            clearItems();
            return;
        }
        clearItems(item::matches);
    }

    @Override
    public void clearItems(GameItem item) {
        if (item == null) {
            clearItems();
            return;
        }
        ItemStack bukkitItem = item.bukkitItem();
        clearItems(stack -> stack != null && stack.isSimilar(bukkitItem));
    }

    public void playSound(Sound sound) {
        playSound(sound, SoundCategory.MASTER, null, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, GameLocation location) {
        playSound(sound, SoundCategory.MASTER, location, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track) {
        playSound(sound, track, null, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location) {
        playSound(sound, track, location, 1f, 1f, 0f);
    }

    public void playSound(Sound sound, float volume) {
        playSound(sound, SoundCategory.MASTER, null, volume, 1f, 0f);
    }

    public void playSound(Sound sound, GameLocation location, float volume) {
        playSound(sound, SoundCategory.MASTER, location, volume, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, float volume) {
        playSound(sound, track, null, volume, 1f, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume) {
        playSound(sound, track, location, volume, 1f, 0f);
    }

    public void playSound(Sound sound, float volume, float pitch) {
        playSound(sound, SoundCategory.MASTER, null, volume, pitch, 0f);
    }

    public void playSound(Sound sound, GameLocation location, float volume, float pitch) {
        playSound(sound, SoundCategory.MASTER, location, volume, pitch, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, float volume, float pitch) {
        playSound(sound, track, null, volume, pitch, 0f);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume, float pitch) {
        playSound(sound, track, location, volume, pitch, 0f);
    }

    public void playSound(Sound sound, float volume, float pitch, float minVolume) {
        playSound(sound, SoundCategory.MASTER, null, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, GameLocation location, float volume, float pitch, float minVolume) {
        playSound(sound, SoundCategory.MASTER, location, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, SoundCategory track, float volume, float pitch, float minVolume) {
        playSound(sound, track, null, volume, pitch, minVolume);
    }

    public void playSound(Sound sound, SoundCategory track, GameLocation location, float volume, float pitch, float minVolume) {
        module().uiManager().playSound(this, sound, track, location, volume, pitch, minVolume);
    }

    void remember(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        PlayerAttributeDefaults.repairInvalidCameraDistance(player);
        state = PlayerState.ONLINE;
        World world = player.getWorld();
        lastWorldName = world == null ? null : world.getName();
        lastLocation = player.getLocation().clone();
        if (spawnPoint == null) {
            spawnPoint = defaultSpawnPoint();
        }
        if (spectatorSession == null) {
            playingState.capture(player);
        } else {
            spectatorSession.apply(player);
        }
    }

    void startFreshSession(Player player) {
        if (player == null) {
            state = PlayerState.OFFLINE;
            return;
        }
        closeSpectatorSession(player);
        SpectatorSession.clearMarker(this, player);
        resetPlayingStateForGame();
        state = PlayerState.ONLINE;
        World world = player.getWorld();
        lastWorldName = world == null ? null : world.getName();
        lastLocation = player.getLocation().clone();
        spawnPoint = defaultSpawnPoint();
        applyPlayingStateIfVisible();
    }

    void markOffline() {
        Player player = bukkitPlayer();
        if (player != null && spectatorSession == null) {
            playingState.capture(player);
        }
        closeSpectatorSession(player);
        state = PlayerState.OFFLINE;
    }

    public void enterPostDeathSpectator(GameLocation location) {
        enterSpectator(location);
        if (pendingPostRespawnTimerLocation != null) {
            int ticks = pendingPostRespawnTimerTicks;
            Supplier<GameLocation> respawnLocation = pendingPostRespawnTimerLocation;
            pendingPostRespawnTimerTicks = 0;
            pendingPostRespawnTimerLocation = null;
            pendingVanillaRespawnLocation = null;
            if (ticks == 0) {
                respawnNow(respawnLocation.get());
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

    public void rememberVanillaDeath() {
        playingState.ageTimedValues();
    }

    void prepareForRemoval() {
        cancelRespawn();
        forcePlayingState("removal");
    }

    void prepareForWorldExit() {
        cancelRespawn();
        forcePlayingState("world exit");
    }

    private void respawnNow(GameLocation location) {
        Player player = bukkitPlayer();
        if (player != null && player.isDead()) {
            pendingVanillaRespawnLocation = location == null ? spawnPoint() : location;
            return;
        }
        if (location != null) {
            setSpawnPoint(location);
        }
        exitSpectator(location == null ? spawnPoint() : location);
        revivePlayingState(maxHealth());
        applyPlayingStateIfVisible();
        player = bukkitPlayer();
        if (player != null) {
            player.setNoDamageTicks(20);
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
        if (spectatorSession == null) {
            Player player = bukkitPlayer();
            if (player != null) {
                playingState.capture(player);
            }
        } else {
            playingState.ageTimedValues();
        }
    }

    private void applyPlayingStateIfVisible() {
        Player player = bukkitPlayer();
        if (player != null && spectatorSession == null) {
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
        Player player = bukkitPlayer();
        if (player != null && player.isDead()) {
            return;
        }
        if (spectatorSession == null) {
            if (player != null) {
                playingState.capture(player);
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
        Player player = bukkitPlayer();
        closeSpectatorSession(player);
        assertNoSpectatorOverlay(player);
        if (player != null && location != null) {
            teleport(location);
        }
        applyPlayingStateIfVisible();
    }

    private void closeSpectatorSession() {
        closeSpectatorSession(bukkitPlayer());
    }

    private void closeSpectatorSession(Player player) {
        SpectatorSession session = spectatorSession;
        spectatorSession = null;
        if (session != null) {
            session.close(player);
        }
    }

    private void assertNoSpectatorOverlay(Player player) {
        if (spectatorSession != null) {
            throw new IllegalStateException("Player " + uuid + " still has an active Donutgame spectator session after spectator exit.");
        }
        if (SpectatorSession.hasMarker(this, player)) {
            SpectatorSession.clearMarker(this, player);
        }
    }

    private void revivePlayingState(double maxHealth) {
        playingState.health = maxHealth;
        playingState.foodLevel = 20;
        playingState.saturation = 20;
        playingState.effects.clear();
        playingState.arrowsInBody = 0;
        playingState.fireTicks = 0;
        playingState.invulnerable = false;
        playingState.invisible = false;
        playingState.canPickupItems = true;
        playingState.gameMode = playingState.gameMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : playingState.gameMode;
        playingState.allowFlight = playingState.gameMode == GameMode.CREATIVE;
        playingState.flying = false;
    }

    private void resetPlayingStateForGame() {
        playingState.reset(GameMode.SURVIVAL);
        playingState.gameMode = GameMode.ADVENTURE;
        playingState.allowFlight = false;
        playingState.flying = false;
        playingState.invulnerable = false;
        playingState.invisible = false;
        playingState.canPickupItems = true;
        playingState.health = maxHealth();
        playingState.foodLevel = 20;
        playingState.saturation = 20.0f;
        playingState.fireTicks = 0;
        playingState.arrowsInBody = 0;
    }

    private void forcePlayingState(String transition) {
        Player player = bukkitPlayer();
        closeSpectatorSession(player);
        assertNoSpectatorOverlay(player);
        if (player == null) {
            return;
        }
        player.setInvulnerable(playingState.invulnerable);
        player.setInvisible(playingState.invisible);
        player.setCanPickupItems(playingState.canPickupItems);
        player.setAllowFlight(playingState.allowFlight);
        player.setFlying(playingState.allowFlight && playingState.flying);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(playingState.gameMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : playingState.gameMode);
        }
        if (SpectatorSession.hasMarker(this, player) || player.isInvulnerable() != playingState.invulnerable || player.isInvisible() != playingState.invisible) {
            throw new IllegalStateException("Player " + uuid + " still has spectator state during " + transition + ".");
        }
        player.updateInventory();
    }

    private Player visiblePlayer() {
        Player player = bukkitPlayer();
        return player != null && spectatorSession == null ? player : null;
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

    private void setPlayerInventorySlot(Player player, EquipmentSlot slot, ItemStack item) {
        switch (slot) {
            case HAND -> player.getInventory().setItemInMainHand(item);
            case OFF_HAND -> player.getInventory().setItemInOffHand(item);
            case FEET -> player.getInventory().setBoots(item);
            case LEGS -> player.getInventory().setLeggings(item);
            case CHEST -> player.getInventory().setChestplate(item);
            case HEAD -> player.getInventory().setHelmet(item);
            default -> {
            }
        }
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
        Player player = bukkitPlayer();
        if (player != null) {
            return new GameLocation(world(), player.getLocation());
        }
        return spawnPoint();
    }

    private GameLocation defaultSpawnPoint() {
        GameWorld world = world();
        if (world == null) {
            return null;
        }
        GameLocation point = world.getPoint("spawn");
        return point == null ? world.worldSpawn() : point;
    }

    private double maxHealth() {
        Player player = bukkitPlayer();
        AttributeInstance instance = player == null ? null : player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }

}
