package com.donutsforlife11.donutgame.internal.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;

public class GameKillCreditEvents implements Listener {
    private static final long KILL_CREDIT_MILLIS = 60_000L;
    private static final long BLOCK_CREDIT_MILLIS = 90_000L;

    // TODO: spectator damage and kill credit handling
    // TODO: custom projectile implementations (snowball egg stuff)

    private final ModuleService moduleService;
    private final Map<UUID, DamageCredit> damageCredits = new HashMap<>();
    private final Map<BlockKey, DamageCredit> blockCredits = new HashMap<>();

    public GameKillCreditEvents(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player attacker = attackingPlayer(event);
        if (attacker == null || attacker == target) {
            return;
        }
        rememberDamageCredit(target, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyEnvironmentalKillCreditBeforeDeath(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (attackingPlayer(event) != null || target.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        applyEnvironmentalKillCredit(target);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void applyEnvironmentalKillCreditOnDeath(PlayerDeathEvent event) {
        Player target = event.getPlayer();
        if (target.getKiller() == null) {
            applyEnvironmentalKillCredit(target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (game(player) == null) {
            return;
        }
        rememberBlockCredit(event.getBlockPlaced(), player);
        rememberNearbyBlockInteraction(event.getBlockPlaced(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (game(player) == null) {
            return;
        }
        rememberBlockCredit(event.getBlock(), player);
        rememberNearbyBlockInteraction(event.getBlock(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberBlockBreak(BlockBreakEvent event) {
        if (game(event.getPlayer()) == null) {
            return;
        }
        rememberNearbyBlockInteraction(event.getBlock(), event.getPlayer());
        blockCredits.remove(BlockKey.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void propagateFlowingBlockCredit(BlockFromToEvent event) {
        DamageCredit credit = blockCredits.get(BlockKey.of(event.getBlock()));
        if (credit != null && !credit.blockExpired()) {
            blockCredits.put(BlockKey.of(event.getToBlock()), new DamageCredit(credit.attackerId(), System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberIgnitedBlock(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null && game(player) != null) {
            rememberBlockCredit(event.getBlock(), player);
            return;
        }
        Block ignitingBlock = event.getIgnitingBlock();
        if (ignitingBlock == null) {
            return;
        }
        DamageCredit credit = blockCredits.get(BlockKey.of(ignitingBlock));
        if (credit != null && !credit.blockExpired()) {
            blockCredits.put(BlockKey.of(event.getBlock()), new DamageCredit(credit.attackerId(), System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void showKillIndicator(PlayerDeathEvent event) {
        Player target = event.getPlayer();
        Player attacker = target.getKiller();
        if (attacker == null && event.getDamageSource().getCausingEntity() instanceof Player source) {
            attacker = source;
        }
        if (attacker == null || attacker == target) {
            return;
        }
        damageCredits.remove(target.getUniqueId());

        GameModule game = game(attacker);
        if (game == null || game(target) != game || !allowsKillCredit(game, target, attacker)) {
            return;
        }
        GamePlayer attackerPlayer = game.playerManager().getPlayer(attacker);
        if (attackerPlayer == null) {
            return;
        }

        var team = target.getScoreboard().getPlayerTeam(target);
        attackerPlayer.playSound(Sound.ITEM_TRIDENT_RETURN, 1.3f, 0.35f);
        attackerPlayer.playSound(Sound.ITEM_TRIDENT_RETURN, 1.3f, 1.15f);
        attackerPlayer.subtitle(Component.empty()
            .append(Component.text("\uD83D\uDDE1 "))
            .append(Component.text(target.getName() + " ", team == null ? NamedTextColor.WHITE : team.color()))
            .append(Component.object(ObjectContents.playerHead(target))));
    }

    private void applyEnvironmentalKillCredit(Player target) {
        DamageCredit credit = bestEnvironmentalCredit(target);
        if (credit == null) {
            return;
        }
        Player attacker = target.getServer().getPlayer(credit.attackerId());
        if (attacker == null || attacker == target) {
            return;
        }
        GameModule game = game(target);
        if (game == null || game(attacker) != game || !allowsKillCredit(game, target, attacker)) {
            return;
        }
        target.setKiller(attacker);
        damageCredits.put(target.getUniqueId(), credit);
    }

    private void rememberDamageCredit(Player target, Player attacker) {
        GameModule game = game(target);
        if (game == null || game(attacker) != game || !allowsKillCredit(game, target, attacker)) {
            return;
        }
        damageCredits.put(target.getUniqueId(), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private boolean allowsKillCredit(GameModule game, Player target, Player attacker) {
        GamePlayer targetPlayer = game.playerManager().getPlayer(target);
        GamePlayer attackerPlayer = game.playerManager().getPlayer(attacker);
        GameTeam team = game.teamManager().getPlayerTeam(targetPlayer);
        return team == null || team != game.teamManager().getPlayerTeam(attackerPlayer) || team.friendlyFireKillCredit();
    }

    private void rememberBlockCredit(Block block, Player attacker) {
        GameModule game = game(attacker);
        if (game == null || !inGameWorld(game, block.getWorld())) {
            return;
        }
        pruneBlockCredits();
        blockCredits.put(BlockKey.of(block), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private void rememberNearbyBlockInteraction(Block block, Player attacker) {
        GameModule game = game(attacker);
        if (game == null) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (GamePlayer candidate : game.playerManager().getOnlinePlayers()) {
            Player target = candidate.bukkitPlayer();
            if (target == null || target == attacker || target.getWorld() != block.getWorld()) {
                continue;
            }
            Location location = target.getLocation();
            double dx = Math.abs(location.getX() - center.getX());
            double dz = Math.abs(location.getZ() - center.getZ());
            double dy = location.getY() - block.getY();
            if (dx <= 1.25 && dz <= 1.25 && dy >= 0.0 && dy <= 3.0) {
                rememberDamageCredit(target, attacker);
            }
        }
    }

    private DamageCredit bestEnvironmentalCredit(Player target) {
        DamageCredit credit = damageCredits.get(target.getUniqueId());
        if (credit != null && !credit.expired()) {
            return credit;
        }
        damageCredits.remove(target.getUniqueId());

        credit = nearbyBlockCredit(target.getLocation());
        return credit == null || credit.blockExpired() ? null : credit;
    }

    private DamageCredit nearbyBlockCredit(Location location) {
        pruneBlockCredits();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    DamageCredit credit = blockCredits.get(BlockKey.of(location.clone().add(x, y, z)));
                    if (credit != null && !credit.blockExpired()) {
                        return credit;
                    }
                }
            }
        }
        return null;
    }

    private void pruneBlockCredits() {
        blockCredits.entrySet().removeIf(entry -> entry.getValue().blockExpired());
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

    private GameModule game(Player player) {
        return moduleService.getGameOfPlayer(player);
    }

    private boolean inGameWorld(GameModule game, World world) {
        return game != null && game.world() != null && world != null && world.equals(game.world().bukkitWorld());
    }

    private record DamageCredit(UUID attackerId, long timeMillis) {
        boolean expired() {
            return System.currentTimeMillis() - timeMillis > KILL_CREDIT_MILLIS;
        }

        boolean blockExpired() {
            return System.currentTimeMillis() - timeMillis > BLOCK_CREDIT_MILLIS;
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        static BlockKey of(Location location) {
            World world = location.getWorld();
            return new BlockKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
