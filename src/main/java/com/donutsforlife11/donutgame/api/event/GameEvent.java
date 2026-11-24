package com.donutsforlife11.donutgame.api.event;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.entity.GameEntityHandle;
import com.donutsforlife11.donutgame.api.map.GameWorld;
import com.donutsforlife11.donutgame.api.map.GameBlock;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.map.GameLocationHandle;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.internal.game.GameModule;

/** Scoped event view. Use it during its handler; unsupported properties throw. */
public final class GameEvent<E extends Event> extends PaperEventMethods {
    private final E event;
    private final GameModule module;
    private boolean open = true;

    GameEvent(E event, GameModule module) {
        this.event = event;
        this.module = module;
    }

    void close() {
        open = false;
    }

    public GamePlayer getPlayer() {
        checkOpen();
        if (event instanceof GamePlayerLeaveEvent leave) return leave.gamePlayer();
        if (event instanceof PlayerEvent playerEvent) {
            return module.playerManager().getPlayer(playerEvent.getPlayer().getUniqueId());
        }
        if (event instanceof BlockPlaceEvent place) {
            return module.playerManager().getPlayer(place.getPlayer().getUniqueId());
        }
        if (event instanceof BlockBreakEvent breakEvent) {
            return module.playerManager().getPlayer(breakEvent.getPlayer().getUniqueId());
        }
        if (event instanceof EntityEvent entityEvent && entityEvent.getEntity() instanceof Player player) {
            return module.playerManager().getPlayer(player.getUniqueId());
        }
        throw unsupported(PlayerEvent.class);
    }

    public GameEntityBase getEntity() {
        return wrapEntity(require(EntityEvent.class).getEntity());
    }

    public GameEntityBase getItemEntity() {
        return wrapEntity(require(org.bukkit.event.entity.EntityPickupItemEvent.class).getItem());
    }

    public int getRemainingItems() {
        return require(org.bukkit.event.entity.EntityPickupItemEvent.class).getRemaining();
    }

    public GameEntityBase getAttacked() {
        return wrapEntity(require(io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class).getAttacked());
    }

    public GamePlayer getKiller() {
        EntityDeathEvent death = require(EntityDeathEvent.class);
        Player killer = death.getEntity().getKiller();
        if (killer == null && death.getDamageSource().getCausingEntity() instanceof Player player) {
            killer = player;
        }
        return killer == null || !GameEventRegistrar.owns(module, killer.getWorld()) ? null
            : module.playerManager().getPlayer(killer.getUniqueId());
    }

    public GamePlayer getAttacker() {
        Entity causing = require(org.bukkit.event.entity.EntityDamageEvent.class).getDamageSource().getCausingEntity();
        return causing instanceof Player player && GameEventRegistrar.owns(module, player.getWorld())
            ? module.playerManager().getPlayer(player.getUniqueId()) : null;
    }

    public GamePlayer getShooter() {
        Entity source = require(EntityEvent.class).getEntity();
        if (!(source instanceof org.bukkit.entity.Projectile projectile) || !(projectile.getShooter() instanceof Player player)) return null;
        return GameEventRegistrar.owns(module, player.getWorld()) ? module.playerManager().getPlayer(player.getUniqueId()) : null;
    }

    public GameBlock getBlock() {
        return wrapBlock(require(BlockEvent.class).getBlock());
    }

    public GameBlock getBlockPlaced() {
        return wrapBlock(require(BlockPlaceEvent.class).getBlockPlaced());
    }

    public GameLocation getFrom() {
        return GameLocationHandle.create(module.world(), require(PlayerMoveEvent.class).getFrom());
    }

    public GameLocation getTo() {
        return GameLocationHandle.create(module.world(), require(PlayerMoveEvent.class).getTo());
    }

    public void setTo(GameLocation location) {
        require(PlayerMoveEvent.class).setTo(GameLocationHandle.toBukkit(location, module.world()));
    }

    public void setRespawnLocation(GameLocation location) {
        require(PlayerRespawnEvent.class).setRespawnLocation(GameLocationHandle.toBukkit(location, module.world()));
    }

    public boolean isCancelled() {
        return require(Cancellable.class).isCancelled();
    }

    public void setCancelled(boolean cancelled) {
        require(Cancellable.class).setCancelled(cancelled);
    }

    public void clearDrops() {
        require(EntityDeathEvent.class).getDrops().clear();
    }

    public void removeDropsIf(Predicate<? super com.donutsforlife11.donutgame.api.item.GameItem> predicate) {
        require(EntityDeathEvent.class).getDrops().removeIf(item -> predicate.test(com.donutsforlife11.donutgame.api.item.GameItem.from(item)));
    }

    public void setDroppedExp(int experience) {
        require(EntityDeathEvent.class).setDroppedExp(experience);
    }

    public void setKeepInventory(boolean keep) {
        require(PlayerDeathEvent.class).setKeepInventory(keep);
    }

    public void setKeepLevel(boolean keep) {
        require(PlayerDeathEvent.class).setKeepLevel(keep);
    }

    public void removeBlocksIf(Predicate<? super GameBlock> predicate) {
        checkOpen();
        Objects.requireNonNull(predicate, "predicate");
        List<Block> blocks = event instanceof EntityExplodeEvent explosion ? explosion.blockList()
            : require(BlockExplodeEvent.class).blockList();
        if (blocks.stream().anyMatch(block -> !GameEventRegistrar.owns(module, block.getWorld()))) {
            throw new IllegalStateException("Explosion contains blocks from another world.");
        }
        blocks.removeIf(block -> predicate.test(wrapBlock(block)));
    }

    public void setSpectatorLocation(GameLocation location) {
        require(GamePlayerLateJoinEvent.class).setSpectatorLocation(location);
    }

    public void setJoinAsSpectator(boolean spectator) {
        require(GamePlayerLateJoinEvent.class).setJoinAsSpectator(spectator);
    }

    @Override
    GameBlock wrapBlock(Block block) {
        return block == null ? null : module.world().blockAt(GameLocationHandle.create(module.world(), block.getLocation()));
    }

    @Override
    GameEntityBase wrapEntity(Entity entity) {
        return GameEntityHandle.wrap(module.world(), entity);
    }

    @Override
    GameWorld scopedWorld() {
        return module.world();
    }

    @Override
    <T> T require(Class<T> type) {
        checkOpen();
        if (!type.isInstance(event)) {
            throw unsupported(type);
        }
        return type.cast(event);
    }

    private void checkOpen() {
        if (!open || !module.acceptsLiveOperations()) {
            throw new IllegalStateException("Game event is no longer being dispatched.");
        }
    }

    private UnsupportedOperationException unsupported(Class<?> type) {
        return new UnsupportedOperationException(event.getEventName() + " does not have this " + type.getSimpleName() + " property.");
    }
}
