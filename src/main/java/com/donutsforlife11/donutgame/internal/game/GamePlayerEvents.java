package com.donutsforlife11.donutgame.internal.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;

public class GamePlayerEvents implements Listener {
    private static final long KILL_CREDIT_MILLIS = 60_000L;
    private static final long BLOCK_CREDIT_MILLIS = 90_000L;

    private final ModuleService moduleService;
    private final Map<UUID, DamageCredit> damageCredits = new HashMap<>();
    private final Map<BlockKey, DamageCredit> blockCredits = new HashMap<>();

    public GamePlayerEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler
    public void playerRespawn(PlayerRespawnEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null) return;
        if (gamePlayer.spawnPoint() != null) event.setRespawnLocation(gamePlayer.spawnPoint().toBukkit(gamePlayer.world().bukkitWorld()));
        GameModule game = game(event.getPlayer());
        moduleService.plugin().getServer().getScheduler().runTask(moduleService.plugin(), () -> {
            gamePlayer.syncStateAfterTrackingReset();
            if (game != null) game.uiManager().refreshPlayerStateAfterTrackingReset(gamePlayer);
        });
    }

    @EventHandler
    public void playerChangedWorld(PlayerChangedWorldEvent event) {
        GameModule game = game(event.getPlayer());
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        moduleService.plugin().getServer().getScheduler().runTask(moduleService.plugin(), () -> {
            if (gamePlayer != null) gamePlayer.syncStateAfterTrackingReset();
            if (game != null) game.uiManager().refreshPlayerStateAfterTrackingReset(gamePlayer);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        GameModule game = game(event.getPlayer());
        if (game != null) game.playerManager().handlePlayerJoin(event.getPlayer());
        if (game != null) game.uiManager().refreshPlayerState();
        if (game == null && moduleService.singleWorld()) {
            GameModule activeGame = moduleService.primaryActiveGame();
            if (activeGame != null && activeGame.world() != null) {
                GameLocation spawn = activeGame.world().getPoint("spawn");
                if (spawn == null) spawn = activeGame.world().worldSpawn();
                event.getPlayer().teleport(spawn.toBukkit(activeGame.world().bukkitWorld()));
                event.getPlayer().setGameMode(org.bukkit.GameMode.ADVENTURE);
                event.getPlayer().setAllowFlight(true);
                event.getPlayer().setFlying(true);
                event.getPlayer().setInvulnerable(true);
                event.getPlayer().setInvisible(true);
                event.getPlayer().setCanPickupItems(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitStorage(PlayerQuitEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null) gamePlayer.restoreBeforeDisconnectSave();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorInteract(PlayerInteractEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer == null || !gamePlayer.isSpectator() || isSpectatorCompass(event.getItem())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorBlockPlace(BlockPlaceEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorBlockBreak(BlockBreakEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorAttemptPickup(PlayerAttemptPickupItemEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) {
            event.setFlyAtPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpectatorPickupExperience(PlayerPickupExperienceEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectatorExperience(PlayerExpChangeEvent event) {
        GamePlayer gamePlayer = gamePlayer(event.getPlayer());
        if (gamePlayer != null && gamePlayer.isSpectator()) event.setAmount(0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) return;
        Player attacker = attackingPlayer(event);
        GamePlayer target = gamePlayer(targetPlayer);
        if (target != null && target.isSpectator()) {
            event.setCancelled(true);
            return;
        }
        if (attacker == null || attacker == targetPlayer) return;
        GamePlayer source = gamePlayer(attacker);
        if (source != null && source.isSpectator()) {
            event.setCancelled(true);
            return;
        }
        if (target == null || source == null) return;
        GameTeam sourceTeam = source.team();
        if (sourceTeam != null && sourceTeam == target.team() && !sourceTeam.allowsFriendlyFireDamage(event.getDamageSource().getDamageType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) return;
        Player attacker = attackingPlayer(event);
        if (attacker == null || attacker == targetPlayer) return;
        rememberDamageCredit(targetPlayer, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyGenerousKillCredit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) return;
        UUID targetId = targetPlayer.getUniqueId();
        Player directAttacker = attackingPlayer(event);
        if (directAttacker != null) return;
        if (targetPlayer.getHealth() - event.getFinalDamage() > 0) return;
        DamageCredit credit = bestEnvironmentalCredit(targetPlayer);
        if (credit == null) return;
        Player attacker = moduleService.plugin().getServer().getPlayer(credit.attackerId());
        if (attacker == null || attacker == targetPlayer) return;
        GameModule game = game(targetPlayer);
        if (game == null || game(attacker) != game) return;
        if (!allowsKillCredit(game, targetPlayer, attacker)) return;
        targetPlayer.setKiller(attacker);
        damageCredits.put(targetId, credit);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void suppressFriendlyKillCredit(PlayerDeathEvent event) {
        Player targetPlayer = event.getPlayer();
        Player attacker = targetPlayer.getKiller();
        if (attacker == null && event.getDamageSource().getCausingEntity() instanceof Player source) {
            attacker = source;
        }
        if (attacker == null || attacker == targetPlayer) return;
        GameModule game = game(targetPlayer);
        if (game == null || game(attacker) != game) return;
        if (allowsKillCredit(game, targetPlayer, attacker)) return;
        targetPlayer.setKiller(null);
        damageCredits.remove(targetPlayer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void applyGenerousKillCreditOnDeath(PlayerDeathEvent event) {
        Player targetPlayer = event.getPlayer();
        if (targetPlayer.getKiller() != null) return;
        DamageCredit credit = bestEnvironmentalCredit(targetPlayer);
        if (credit == null) return;
        Player attacker = moduleService.plugin().getServer().getPlayer(credit.attackerId());
        if (attacker == null || attacker == targetPlayer) return;
        GameModule game = game(targetPlayer);
        if (game == null || game(attacker) != game) return;
        if (!allowsKillCredit(game, targetPlayer, attacker)) return;
        targetPlayer.setKiller(attacker);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (gamePlayer(player) == null) return;
        rememberBlockCredit(event.getBlockPlaced(), player);
        rememberNearbyBlockInteraction(event.getBlockPlaced(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (gamePlayer(player) == null) return;
        rememberBlockCredit(event.getBlock(), player);
        rememberNearbyBlockInteraction(event.getBlock(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (gamePlayer(player) == null) return;
        rememberNearbyBlockInteraction(event.getBlock(), player);
        blockCredits.remove(BlockKey.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void propagateBlockCredit(BlockFromToEvent event) {
        DamageCredit credit = blockCredits.get(BlockKey.of(event.getBlock()));
        if (credit == null || credit.blockExpired()) return;
        blockCredits.put(BlockKey.of(event.getToBlock()), new DamageCredit(credit.attackerId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberIgnitedBlock(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null && gamePlayer(player) != null) {
            rememberBlockCredit(event.getBlock(), player);
            return;
        }
        Block ignitingBlock = event.getIgnitingBlock();
        if (ignitingBlock == null) return;
        DamageCredit credit = blockCredits.get(BlockKey.of(ignitingBlock));
        if (credit == null || credit.blockExpired()) return;
        blockCredits.put(BlockKey.of(event.getBlock()), new DamageCredit(credit.attackerId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onThrowableHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball || projectile instanceof Egg)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        GameModule game = game(projectile);
        if (game == null) return;
        if (target instanceof Player player) {
            GamePlayer targetPlayer = game.playerManager().getPlayer(player);
            if (targetPlayer != null && targetPlayer.isSpectator()) return;
        }
        Entity shooter = shooter(projectile);
        if (!game.world().pvp() && shooter instanceof Player && target instanceof Player) {
            event.setCancelled(true);
            projectile.remove();
            return;
        }
        if (isBlockedFriendlyThrowable(game, shooter, target)) {
            event.setCancelled(true);
            projectile.remove();
            return;
        }
        event.setCancelled(true);
        if (target instanceof Damageable damageable) damageThrowable(projectile, shooter, target, damageable);
        applyThrowableKnockback(projectile, target);
        projectile.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = gamePlayer(player);
        if (gamePlayer == null || !gamePlayer.isSpectator()) return;
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            GameLocation fallback = gamePlayer.spawnPoint() != null ? gamePlayer.spawnPoint() : gamePlayer.world().worldSpawn();
            gamePlayer.teleport(fallback);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameModule game = game(player);
        if (game == null || !game.world().contains(player)) return;
        GamePlayer gamePlayer = game.playerManager().getPlayer(player);
        if (gamePlayer != null && (gamePlayer.isSpectator() || !game.world().hungerEnabled())) event.setCancelled(true);
    }

    @EventHandler
    public void playerKillIndicator(PlayerDeathEvent event) {
        Player attacker = event.getEntity().getKiller();
        if (attacker == null && event.getDamageSource().getCausingEntity() instanceof Player source) {
            attacker = source;
        }
        if (attacker == null) {
            return;
        }
        damageCredits.remove(event.getPlayer().getUniqueId());
        GameModule attackerGame = game(attacker);
        if (attackerGame == null) {
            return;
        }
        GamePlayer attackerPlayer = attackerGame.playerManager().getPlayer(attacker);
        if (attackerPlayer == null) {
            return;
        }
        Player target = event.getPlayer();
        if (!allowsKillCredit(attackerGame, target, attacker)) {
            return;
        }
        var team = target.getScoreboard().getPlayerTeam(target);
        attackerGame.uiManager().playSound(attackerPlayer, Sound.ITEM_TRIDENT_RETURN, 1.3f, 0.35f);
        attackerGame.uiManager().playSound(attackerPlayer, Sound.ITEM_TRIDENT_RETURN, 1.3f, 1.15f);
        // Don't change this subtitle, I like it and the icon renders just fine
        attackerGame.uiManager().subtitle(attackerPlayer, Component.empty()
            .append(Component.text("🗡 "))
            .append(Component.text(target.getName() + " ", team == null ? NamedTextColor.WHITE : team.color()))
            .append(Component.object(ObjectContents.playerHead(target)))
        );
    }

    private GamePlayer gamePlayer(Player player) {
        GameModule game = game(player);
        return game == null ? null : game.playerManager().getPlayer(player);
    }

    private GameModule game(Entity entity) {
        if (entity instanceof Player player) {
            return game(player);
        }
        for (GameModule game : moduleService.activeGames().values()) {
            if (game.world() != null && game.world().contains(entity)) return game;
        }
        return null;
    }

    private GameModule game(Player player) {
        try {
            return moduleService.getGameOfPlayer(player);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Player attackingPlayer(EntityDamageEvent event) {
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }
        Entity directEntity = event.getDamageSource().getDirectEntity();
        if (directEntity instanceof Player player) {
            return player;
        }
        if (directEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void rememberDamageCredit(Player targetPlayer, Player attacker) {
        GameModule game = game(targetPlayer);
        if (game == null || game(attacker) != game) return;
        GamePlayer target = game.playerManager().getPlayer(targetPlayer);
        GamePlayer source = game.playerManager().getPlayer(attacker);
        if (target == null || source == null || target.isSpectator() || source.isSpectator()) return;
        if (!allowsKillCredit(game, targetPlayer, attacker)) return;
        damageCredits.put(targetPlayer.getUniqueId(), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private void rememberBlockCredit(Block block, Player attacker) {
        GameModule game = game(attacker);
        if (game == null || !game.world().contains(block.getLocation())) return;
        GamePlayer source = game.playerManager().getPlayer(attacker);
        if (source == null || source.isSpectator()) return;
        pruneBlockCredits();
        blockCredits.put(BlockKey.of(block), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private void rememberNearbyBlockInteraction(Block block, Player attacker) {
        GameModule game = game(attacker);
        if (game == null) return;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (GamePlayer candidate : game.playerManager().getPlayers()) {
            Player target = candidate.player();
            if (target == null || target == attacker || candidate.isSpectator()) continue;
            if (target.getWorld() != block.getWorld()) continue;
            Location location = target.getLocation();
            double dx = Math.abs(location.getX() - center.getX());
            double dz = Math.abs(location.getZ() - center.getZ());
            double dy = location.getY() - block.getY();
            if (dx <= 1.25 && dz <= 1.25 && dy >= 0.0 && dy <= 3.0) {
                rememberDamageCredit(target, attacker);
            }
        }
    }

    private DamageCredit bestEnvironmentalCredit(Player targetPlayer) {
        DamageCredit credit = damageCredits.get(targetPlayer.getUniqueId());
        if (credit != null && !credit.expired()) return credit;
        if (credit != null) damageCredits.remove(targetPlayer.getUniqueId());
        credit = nearbyBlockCredit(targetPlayer.getLocation());
        return credit != null && !credit.blockExpired() ? credit : null;
    }

    private DamageCredit nearbyBlockCredit(Location location) {
        pruneBlockCredits();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    DamageCredit credit = blockCredits.get(BlockKey.of(location.clone().add(x, y, z)));
                    if (credit != null && !credit.blockExpired()) return credit;
                }
            }
        }
        return null;
    }

    private void pruneBlockCredits() {
        blockCredits.entrySet().removeIf(entry -> entry.getValue().blockExpired());
    }

    private Entity shooter(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Entity entity ? entity : null;
    }

    private boolean isBlockedFriendlyThrowable(GameModule game, Entity shooter, LivingEntity target) {
        if (!(shooter instanceof Player attacker) || !(target instanceof Player targetPlayer)) return false;
        GamePlayer source = game.playerManager().getPlayer(attacker);
        GamePlayer victim = game.playerManager().getPlayer(targetPlayer);
        if (source == null || victim == null || source == victim) return false;
        GameTeam team = source.team();
        return team != null && team == victim.team() && !team.allowsFriendlyFireDamage(DamageType.THROWN);
    }

    private boolean allowsKillCredit(GameModule game, Player targetPlayer, Player attacker) {
        if (game == null || targetPlayer == null || attacker == null || targetPlayer.equals(attacker)) return false;
        GamePlayer target = game.playerManager().getPlayer(targetPlayer);
        GamePlayer source = game.playerManager().getPlayer(attacker);
        if (target == null || source == null) return true;
        GameTeam team = source.team();
        return team == null || team != target.team() || team.friendlyFire();
    }

    private void damageThrowable(Projectile projectile, Entity shooter, LivingEntity target, Damageable damageable) {
        DamageSource.Builder source = DamageSource.builder(DamageType.THROWN).withDirectEntity(projectile);
        if (shooter != null) source.withCausingEntity(shooter);
        damageable.damage(projectile instanceof Egg ? 0.01 : 0.5, source.build());
    }

    private void applyThrowableKnockback(Projectile projectile, LivingEntity target) {
        Vector direction = projectile.getVelocity();
        if (direction.lengthSquared() < 0.001) {
            direction = target.getLocation().toVector().subtract(projectile.getLocation().toVector());
        }
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(0.5);
        direction.setY(Math.max(0.22, direction.getY() + 0.12));
        target.setVelocity(target.getVelocity().add(direction));
    }

    private boolean isSpectatorCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && GamePlayer.isSpectatorCompass(item);
    }

    private record DamageCredit(UUID attackerId, long timeMillis) {
        private boolean expired() {
            return System.currentTimeMillis() - timeMillis > KILL_CREDIT_MILLIS;
        }

        private boolean blockExpired() {
            return System.currentTimeMillis() - timeMillis > BLOCK_CREDIT_MILLIS;
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(Block block) {
            World world = block.getWorld();
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }

        private static BlockKey of(Location location) {
            World world = location.getWorld();
            return new BlockKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
