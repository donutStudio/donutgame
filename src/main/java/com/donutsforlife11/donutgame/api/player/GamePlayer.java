package com.donutsforlife11.donutgame.api.player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.api.time.GameTimer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GamePlayer extends GameEntity {
    private final PlayerManager playerManager;

    private boolean spectator;
    private GameLocation respawnLocation;
    private GameTimer respawnTimer;
    private ItemStack[] storedInventory;
    private ItemStack[] storedArmor;
    private ItemStack storedOffhand;

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

    public void setSpectator() {
        setSpectator(location());
    }

    public void setSpectator(GameLocation location) {
        spectator = true;
        Player player = player();
        if (player == null) {
            return;
        }
        storeInventoryIfNeeded(player);
        if (location != null) {
            teleport(location);
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setInvisible(true);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setFallDistance(0);
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void setNonSpectator() {
        spectator = false;
        Player player = player();
        if (player == null) {
            return;
        }
        restoreInventoryIfNeeded(player);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGameMode(GameMode.SURVIVAL);
        playerManager.module().uiManager().refreshPlayerState();
    }

    public void addEffect(PotionEffectType effect, int ticks, int amplifier) {
        if (effect == null) {
            throw new IllegalArgumentException("Effect cannot be null.");
        }
        if (ticks < 0 && ticks != PotionEffect.INFINITE_DURATION) {
            throw new IllegalArgumentException("Effect duration cannot be negative.");
        }
        if (amplifier < 0) {
            throw new IllegalArgumentException("Effect amplifier cannot be negative.");
        }
        Player player = player();
        if (player != null) {
            player.addPotionEffect(new PotionEffect(effect, ticks, amplifier));
        }
    }

    public void removeEffect(PotionEffectType effect) {
        Player player = player();
        if (player != null) {
            player.removePotionEffect(effect);
        }
    }

    public Collection<PotionEffect> getEffects() {
        Player player = player();
        return player == null ? Collections.emptyList() : player.getActivePotionEffects();
    }

    public void clearEffects() {
        Player player = player();
        if (player == null) {
            return;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void respawn() {
        respawn(0, () -> respawnLocation);
    }

    public void respawn(int ticks) {
        respawn(ticks, () -> respawnLocation);
    }

    public void respawn(int ticks, Supplier<GameLocation> locationSupplier) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative.");
        }
        cancelRespawn();
        setSpectator();
        respawnTimer = playerManager.module().timeManager().newTimer(ticks).onTick(20, timer -> {
            int remainingSeconds = Math.round(timer.getRemainingTicks() / 20.0f);
            playerManager.module().uiManager().actionbar(
                this,
                Component.text("Respawning in: ")
                    .append(Component.text(String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60), NamedTextColor.GREEN))
            );
        }).onFinish(ignored -> {
            respawnTimer = null;
            if (!playerManager.isRegistered(this) || player() == null) {
                return;
            }
            GameLocation location = locationSupplier.get();
            if (location != null) {
                setRespawnLocation(location);
                teleport(location);
            }
            setNonSpectator();
        }).start();
    }

    public void cancelRespawn() {
        if (respawnTimer != null) {
            respawnTimer.cancel();
            respawnTimer = null;
        }
    }

    public void setRespawnLocation(GameLocation location) {
        respawnLocation = location;
        Player player = player();
        if (player != null && location != null) {
            player.setRespawnLocation(location.toBukkit(world().bukkitWorld()), true);
        }
    }

    public Map<Integer, ItemStack> addToStoredInventory(
        Collection<ItemStack> items
    ) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        /*
        * A spectator should normally already have this because
        * setSpectator() snapshots their inventory before clearing it.
        */
        if (storedInventory == null) {
            storedInventory = new ItemStack[36];
        }

        Inventory temporaryInventory = Bukkit.createInventory(null, 36);

        temporaryInventory.setContents(cloneContents(storedInventory));

        Map<Integer, ItemStack> leftovers = temporaryInventory.addItem(
            items.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new)
        );

        storedInventory = cloneContents(temporaryInventory.getContents());

        return leftovers;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public String getName() {
        Player player = player();
        return player == null ? uuid().toString() : player.getName();
    }

    public void setGameMode(GameMode gameMode) {
        Player player = player();
        if (player != null) {
            player.setGameMode(gameMode);
        }
    }

    public GameMode gameMode() {
        Player player = player();
        return player == null ? GameMode.SPECTATOR : player.getGameMode();
    }

    public boolean isOnline() {
        return player() != null;
    }

    public GameLocation respawnLocation() {
        return respawnLocation;
    }

    public GameTimer respawnTimer() {
        return respawnTimer;
    }

    public void clearInventory() {
        Player player = player();
        if (player != null) {
            player.getInventory().clear();
        }
    }

    public void setFoodLevel(int foodLevel) {
        Player player = player();
        if (player != null) {
            player.setFoodLevel(foodLevel);
        }
    }

    public void setSaturation(float saturation) {
        Player player = player();
        if (player != null) {
            player.setSaturation(saturation);
        }
    }

    public void clearExperience() {
        Player player = player();
        if (player == null) {
            return;
        }
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
    }

    public GameTeam team() {
        return playerManager.module().teamManager().getPlayerTeam(this);
    }

    public Collection<GamePlayer> teammates() {
        GameTeam team = team();
        return team == null ? Set.of() : team.getMembers();
    }

    public GamePlayer closestTeammate(Predicate<GamePlayer> filter) {
        GamePlayer closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (GamePlayer teammate : teammates()) {
            if (teammate.uuid().equals(uuid()) || !filter.test(teammate) || teammate.player() == null) {
                continue;
            }
            double distance = teammate.location().distanceSquared(location());
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = teammate;
            }
        }
        return closest;
    }

    private void storeInventoryIfNeeded(Player player) {
        if (storedInventory != null) {
            return;
        }
        storedInventory = cloneContents(
            player.getInventory().getStorageContents()
        );
        storedArmor = cloneContents(
            player.getInventory().getArmorContents()
        );
        ItemStack offhand = player.getInventory().getItemInOffHand();
        storedOffhand = offhand == null
            ? null
            : offhand.clone();
    }

    private void restoreInventoryIfNeeded(Player player) {
        if (storedInventory == null) {
            return;
        }
        player.getInventory().setStorageContents(storedInventory);
        player.getInventory().setArmorContents(storedArmor);
        player.getInventory().setItemInOffHand(storedOffhand);
        storedInventory = null;
        storedArmor = null;
        storedOffhand = null;
    }
    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null
                ? null
                : contents[i].clone();
        }

        return clone;
    }
}
