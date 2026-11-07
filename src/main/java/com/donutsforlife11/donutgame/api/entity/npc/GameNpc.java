package com.donutsforlife11.donutgame.api.entity.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;

/** A scoped, temporary mannequin. All NPC operations run on the server thread. */
public final class GameNpc extends GameEntity {
    final Mannequin mannequin;
    final long worldGeneration;
    final Map<UUID, Integer> interactions = new HashMap<>();
    final Map<UUID, Integer> attacks = new HashMap<>();
    Consumer<GamePlayer> interactAction;
    Consumer<GamePlayer> attackAction;
    boolean jumpRequested;
    private final GameNpcController controller;
    private PlayerProfile profile;
    private Component description = Mannequin.defaultDescription();
    private boolean descriptionVisible;
    private boolean sprinting;
    private int interactionCooldown = 5;
    private int attackCooldown = 5;

    public GameNpc(GameWorld world, long generation, Mannequin mannequin, PlayerProfile profile) {
        super(world, mannequin.getUniqueId(), mannequin.getType());
        this.mannequin = mannequin;
        this.worldGeneration = generation;
        mannequin.setInvulnerable(true);
        mannequin.setPersistent(false);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setImmovable(false);
        mannequin.setDescription(null);
        mannequin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.10000000149011612d);
        mannequin.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(0.6);
        if (mannequin.getAttribute(Attribute.SNEAKING_SPEED) == null) {
            mannequin.registerAttribute(Attribute.SNEAKING_SPEED);
        }
        mannequin.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0.3);
        if (profile == null) {
            ResolvableProfile defaults = Mannequin.defaultProfile();
            this.profile = Bukkit.createProfile(defaults.uuid() == null ? UUID.randomUUID() : defaults.uuid(), defaults.name());
            this.profile.setProperties(defaults.properties());
        } else {
            setProfile(profile);
        }
        controller = new GameNpcController(this);
    }

    public GameEntity entity() {
        return this;
    }

    public void setProfile(PlayerProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile").clone();
        mannequin.setProfile(ResolvableProfile.resolvableProfile(this.profile));
    }

    public PlayerProfile profile() {
        return profile.clone();
    }

    public void setDescription(Component description) {
        this.description = description;
        mannequin.setDescription(descriptionVisible ? description : null);
    }

    public void setDescriptionVisible(boolean visible) {
        descriptionVisible = visible;
        mannequin.setDescription(visible ? description : null);
    }

    public Component description() {
        return description;
    }

    public void setImmovable(boolean immovable) {
        mannequin.setImmovable(immovable);
        if (immovable) {
            mannequin.setVelocity(new Vector());
            if (controller.isNavigating()) {
                controller.finishNavigation(false);
            }
        }
    }

    public boolean isImmovable() {
        return mannequin.isImmovable();
    }

    public void setSneaking(boolean sneaking) {
        mannequin.setSneaking(sneaking);
        mannequin.setPose(sneaking ? Pose.SNEAKING : Pose.STANDING, true);
    }

    public boolean isSneaking() {
        return mannequin.isSneaking();
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void jump() {
        if (!isImmovable() && mannequin.isOnGround()) {
            jumpRequested = true;
            mannequin.setJumping(true);
            if (sprinting && !isSneaking()) {
                double yaw = Math.toRadians(mannequin.getYaw());
                mannequin.setVelocity(mannequin.getVelocity().add(new Vector(-Math.sin(yaw) * 0.2, 0, Math.cos(yaw) * 0.2)));
            }
        }
    }

    public void onInteract(Consumer<GamePlayer> action) {
        interactAction = action;
    }

    public void onAttack(Consumer<GamePlayer> action) {
        attackAction = action;
    }

    public void setInteractionCooldown(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Interaction cooldown cannot be negative.");
        }
        interactionCooldown = ticks;
    }

    public void setAttackCooldown(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Attack cooldown cannot be negative.");
        }
        attackCooldown = ticks;
    }

    public int interactionCooldown() {
        return interactionCooldown;
    }

    public int attackCooldown() {
        return attackCooldown;
    }

    public GameNpcController controller() {
        return controller;
    }

    @Override
    public boolean isDead() {
        return GameWorldHandle.generation(world()) != worldGeneration || !mannequin.isValid() || mannequin.isDead();
    }

    @Override
    public void remove() {
        controller.cancelNavigation();
        interactions.clear();
        attacks.clear();
        mannequin.remove();
        JavaPlugin.getPlugin(Donutgame.class).npcService().npcs.remove(uuid());
    }
}
