package com.donutsforlife11.donutgame.api.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.EventExecutor;

import com.donutsforlife11.donutgame.Donutgame;
import com.donutsforlife11.donutgame.api.map.GameWorldHandle;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.internal.game.GameScope;

public class GameEventRegistrar {
    private final GameModule module;
    private final Donutgame plugin;
    private final Listener listener = new Listener() {};
    private final Set<HandlerKey> registeredHandlers = new HashSet<>();

    public GameEventRegistrar(GameModule module, Donutgame plugin) {
        this.module = module;
        this.plugin = plugin;
    }

    public void unregisterAll() {
        HandlerList.unregisterAll(listener);
        registeredHandlers.clear();
    }

    public void registerAnnotated(Object target) {
        GameScope.requireActive(module);
        Objects.requireNonNull(target, "target");
        for (Class<?> current = target.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                GameEventHandler annotation = method.getAnnotation(GameEventHandler.class);
                if (annotation != null) {
                    register(target, method, annotation);
                }
            }
        }
    }

    private void register(Object target, Method method, GameEventHandler annotation) {
        if (!registeredHandlers.add(new HandlerKey(target, method))) {
            return;
        }
        if (method.isSynthetic() || method.getParameterCount() != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must have exactly one parameter.");
        }
        Class<?> parameterType = method.getParameterTypes()[0];
        if (!GameEvent.class.equals(parameterType)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must accept GameEvent<E>.");
        }
        Class<? extends Event> eventType = eventType(method);
        method.setAccessible(true);

        EventExecutor executor = (ignored, event) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            if (annotation.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                return;
            }
            if (!isInScope(event)) {
                return;
            }
            GameEvent<?> scoped = new GameEvent<>(eventType.cast(event), module);
            try {
                method.invoke(target, scoped);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to call game event handler " + method.getName() + ".", exception);
            } catch (InvocationTargetException exception) {
                module.logError("Game event handler " + method.getName() + " failed.", exception.getTargetException());
            } finally {
                scoped.close();
            }
        };
        plugin.getServer().getPluginManager().registerEvent(eventType, listener, annotation.priority(), executor, plugin, annotation.ignoreCancelled());
    }

    private Class<? extends Event> eventType(Method method) {
        Type type = method.getGenericParameterTypes()[0];
        if (!(type instanceof ParameterizedType parameterized) || parameterized.getActualTypeArguments().length != 1) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must use GameEvent<PaperEventType>.");
        }
        Type eventType = parameterized.getActualTypeArguments()[0];
        if (!(eventType instanceof Class<?> eventClass) || !Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalStateException("@GameEventHandler method " + method.getName() + " must wrap a Paper Event type.");
        }
        return eventClass.asSubclass(Event.class);
    }

    private boolean isInScope(Event event) {
        if (!module.acceptsLiveOperations()) {
            return false;
        }
        // Check subclass cases before their single-actor parent.
        return switch (event) {
            case GamePlayerLateJoinEvent lateJoin -> lateJoin.module() == module && ownsPlayer(lateJoin.getPlayer());
            case org.bukkit.event.player.PlayerMoveEvent move -> ownsPlayer(move.getPlayer())
                && owns(module, move.getFrom().getWorld()) && ownsLocation(move.getTo());
            case org.bukkit.event.player.PlayerRespawnEvent respawn -> ownsPlayer(respawn.getPlayer())
                && ownsLocation(respawn.getRespawnLocation());
            case org.bukkit.event.player.PlayerInteractEntityEvent interact -> ownsPlayer(interact.getPlayer())
                && ownsEntity(interact.getRightClicked());
            case io.papermc.paper.event.player.PrePlayerAttackEntityEvent attack -> ownsPlayer(attack.getPlayer())
                && ownsEntity(attack.getAttacked());
            case org.bukkit.event.player.PlayerInteractEvent interact -> ownsPlayer(interact.getPlayer())
                && (interact.getClickedBlock() == null || ownsBlock(interact.getClickedBlock()));
            case org.bukkit.event.player.PlayerJoinEvent join -> ownsPlayer(join.getPlayer());
            case org.bukkit.event.player.PlayerQuitEvent quit -> ownsPlayer(quit.getPlayer());
            case org.bukkit.event.block.BlockPlaceEvent place -> ownsPlayer(place.getPlayer())
                && ownsBlock(place.getBlockPlaced()) && ownsBlock(place.getBlockAgainst())
                && (!(place instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi)
                    || multi.getReplacedBlockStates().stream().allMatch(state -> owns(module, state.getWorld())));
            case org.bukkit.event.block.BlockBreakEvent broken -> ownsPlayer(broken.getPlayer()) && ownsBlock(broken.getBlock());
            case org.bukkit.event.block.BlockExplodeEvent explosion -> ownsBlock(explosion.getBlock())
                && explosion.blockList().stream().allMatch(this::ownsBlock);
            case org.bukkit.event.entity.EntityExplodeEvent explosion -> ownsEntity(explosion.getEntity())
                && ownsLocation(explosion.getLocation()) && explosion.blockList().stream().allMatch(this::ownsBlock);
            // Death cleanup still runs for the owned victim. getKiller exposes only an owned player.
            case org.bukkit.event.entity.EntityDeathEvent death -> ownsEntity(death.getEntity());
            case org.bukkit.event.entity.EntityDamageEvent damage -> ownsEntity(damage.getEntity())
                && ownsDamage(damage.getDamageSource())
                && (!(damage instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) || ownsEntity(byEntity.getDamager()))
                && (!(damage instanceof org.bukkit.event.entity.EntityDamageByBlockEvent byBlock)
                    || byBlock.getDamager() == null || ownsBlock(byBlock.getDamager()));
            case org.bukkit.event.entity.EntityTargetEvent target -> ownsEntity(target.getEntity())
                && (target.getTarget() == null || ownsEntity(target.getTarget()));
            case org.bukkit.event.entity.ProjectileHitEvent hit -> ownsEntity(hit.getEntity())
                && (hit.getHitEntity() == null || ownsEntity(hit.getHitEntity()))
                && (hit.getHitBlock() == null || ownsBlock(hit.getHitBlock()));
            case org.bukkit.event.entity.ProjectileLaunchEvent launch -> ownsEntity(launch.getEntity());
            default -> false;
        };
    }

    private boolean ownsDamage(org.bukkit.damage.DamageSource damage) {
        return (damage.getCausingEntity() == null || ownsEntity(damage.getCausingEntity()))
            && (damage.getDirectEntity() == null || ownsEntity(damage.getDirectEntity()))
            && ownsLocation(damage.getDamageLocation());
    }

    private boolean ownsEntity(Entity entity) {
        if (!owns(module, entity.getWorld()) || (entity instanceof Player player && !ownsPlayer(player))) {
            return false;
        }
        if (entity instanceof org.bukkit.entity.Projectile projectile) {
            return switch (projectile.getShooter()) {
                case Entity shooter -> owns(module, shooter.getWorld())
                    && (!(shooter instanceof Player player) || ownsPlayer(player));
                case org.bukkit.projectiles.BlockProjectileSource source -> ownsBlock(source.getBlock());
                case null, default -> true;
            };
        }
        return true;
    }

    private boolean ownsPlayer(Player player) {
        return owns(module, player.getWorld()) && module.playerManager().getPlayer(player.getUniqueId()) != null;
    }

    private boolean ownsBlock(Block block) {
        return owns(module, block.getWorld());
    }

    private boolean ownsLocation(Location location) {
        return location == null || owns(module, location.getWorld());
    }

    static boolean owns(GameModule module, World world) {
        return world != null && world == GameWorldHandle.bukkitWorld(module.world());
    }

    private record HandlerKey(Object target, Method method) {
        @Override
        public boolean equals(Object object) {
            return object instanceof HandlerKey other
                && target == other.target
                && method.equals(other.method);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(target) + method.hashCode();
        }
    }
}
