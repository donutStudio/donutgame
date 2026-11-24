package com.donutsforlife11.donutgame.api.entity.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/** Internal lifecycle, input and animation bridge. One ticker serves all NPCs. */
public final class GameNpcService implements Listener {
    private final Donutgame plugin;
    final Map<UUID, GameNpc> npcs = new HashMap<>();
    private final Map<UUID, Rotation> rotations = new HashMap<>();
    private BukkitTask ticker;

    public GameNpcService(Donutgame plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            for (GameNpc npc : List.copyOf(npcs.values())) {
                if (npc.isDead()) {
                    npc.discard();
                    continue;
                }
                npc.mannequin.getChunk().addPluginChunkTicket(plugin);
                // Jump requests are one-shot, rather than continuous bunny hopping.
                npc.mannequin.setJumping(npc.jumpRequested);
                npc.jumpRequested = false;
                if (now % 100 == 0) {
                    npc.interactions.entrySet().removeIf(entry -> now - entry.getValue() >= Math.max(1, npc.interactionCooldown()));
                    npc.attacks.entrySet().removeIf(entry -> now - entry.getValue() >= Math.max(1, npc.attackCooldown()));
                }
                try {
                    npc.controller().tick();
                } catch (RuntimeException exception) {
                    npc.controller().cancelNavigation();
                    plugin.getLogger().log(Level.SEVERE, "NPC navigation failed for " + npc.uuid(), exception);
                }
            }
            for (Rotation rotation : List.copyOf(rotations.values())) {
                Entity entity = rotation.entity();
                if (!entity.isValid() || GameEntityHandle.bukkitEntity(rotation.handle()) == null
                    || !entity.getWorld().equals(rotation.world())) {
                    rotations.remove(entity.getUniqueId(), rotation);
                    continue;
                }
                double fraction = Math.min(1.0, (now - rotation.started()) / (double) rotation.ticks());
                float yaw = (float) (rotation.yaw() + rotation.deltaYaw() * fraction);
                entity.setRotation(yaw, (float) (rotation.pitch() + rotation.deltaPitch() * fraction));
                if (entity instanceof LivingEntity living) {
                    living.setBodyYaw(yaw);
                }
                if (fraction >= 1) {
                    rotations.remove(entity.getUniqueId(), rotation);
                }
            }
        }, 1, 1);
    }

    public void disable() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (GameNpc npc : List.copyOf(npcs.values())) {
            npc.discard();
        }
        rotations.clear();
    }

    public GameNpc register(GameWorld world, Mannequin mannequin, PlayerProfile profile) {
        mannequin.getChunk().addPluginChunkTicket(plugin);
        GameNpc npc = new GameNpc(world, GameWorldHandle.generation(world), mannequin, profile);
        npcs.put(npc.uuid(), npc);
        return npc;
    }

    public GameNpc find(GameWorld world, Entity entity) {
        GameNpc npc = npcs.get(entity.getUniqueId());
        return npc != null && npc.world() == world && npc.isInCurrentWorld() && npc.mannequin.equals(entity) ? npc : null;
    }

    public void rotate(GameEntityBase handle, float yaw, float pitch, int ticks) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || ticks < 0) {
            throw new IllegalArgumentException("Rotation must be finite and ticks cannot be negative.");
        }
        Entity entity = GameEntityHandle.requireBukkitEntity(handle);
        rotations.remove(entity.getUniqueId());
        if (ticks == 0) {
            entity.setRotation(yaw, pitch);
            if (entity instanceof LivingEntity living) {
                living.setBodyYaw(yaw);
            }
        } else {
            org.bukkit.Location start = entity.getLocation();
            float delta = (float) (yaw - start.getYaw() - 360.0 * Math.floor((yaw - start.getYaw() + 180.0) / 360.0));
            rotations.put(entity.getUniqueId(), new Rotation(handle, entity, entity.getWorld(), start.getYaw(), start.getPitch(),
                delta, pitch - start.getPitch(), Bukkit.getCurrentTick(), ticks));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        GameNpc npc = npcs.get(event.getRightClicked().getUniqueId());
        if (npc != null) {
            event.setCancelled(true); // Owned NPCs do not use vanilla mannequin equipment swapping.
            if (event.getHand() == EquipmentSlot.HAND) {
                interact(npc, event.getPlayer(), false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        onInteract(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        GameNpc npc = npcs.get(event.getAttacked().getUniqueId());
        if (npc != null) {
            interact(npc, event.getPlayer(), true);
        }
    }

    private void interact(GameNpc npc, org.bukkit.entity.Player player, boolean attack) {
        GameModule module = plugin.moduleService().getGameOfPlayer(player);
        if (!npc.isInCurrentWorld() || module == null || module.world() != npc.world()) {
            return;
        }
        GamePlayer gamePlayer = module.playerManager().getPlayer(player.getUniqueId());
        if (gamePlayer == null || gamePlayer.isSpectator()) {
            return;
        }
        Map<UUID, Integer> cooldowns = attack ? npc.attacks : npc.interactions;
        int cooldown = attack ? npc.attackCooldown() : npc.interactionCooldown();
        int now = Bukkit.getCurrentTick();
        Integer last = cooldowns.get(player.getUniqueId());
        // AtEntity and Entity packets can describe the same click even with a zero cooldown.
        if (last != null && now - last < Math.max(1, cooldown)) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now);
        var action = attack ? npc.attackAction : npc.interactAction;
        if (action != null) {
            action.accept(gamePlayer);
        }
    }

    private record Rotation(GameEntityBase handle, Entity entity, org.bukkit.World world, float yaw, float pitch,
                            float deltaYaw, float deltaPitch, int started, int ticks) {
    }
}
