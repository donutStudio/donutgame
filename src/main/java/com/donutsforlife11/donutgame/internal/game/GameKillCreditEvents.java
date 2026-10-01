package com.donutsforlife11.donutgame.internal.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

public class GameKillCreditEvents implements Listener {
    private static final long KILL_CREDIT_MILLIS = 60_000L;
    private static final long BLOCK_CREDIT_MILLIS = 90_000L;
    private static final long MULTI_KILL_MILLIS = 10_000L;
    private static final int KILL_SUBTITLE_SPACING_TICKS = 7;
    private final ModuleService moduleService;
    private final Map<UUID, DamageCredit> damageCredits = new HashMap<>();
    private final Map<BlockKey, DamageCredit> blockCredits = new HashMap<>();
    private final Map<UUID, KillStreak> killStreaks = new HashMap<>();
    private final Map<UUID, Deque<KillSubtitle>> killSubtitles = new HashMap<>();

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
        rememberDamageCredit(target, attacker, killIcons(event, null));
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
            blockCredits.put(BlockKey.of(event.getToBlock()), credit.refreshed());
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
            blockCredits.put(BlockKey.of(event.getBlock()), credit.refreshed(KillIcon.FIRE));
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

        DamageCredit credit = damageCredits.remove(target.getUniqueId());
        Set<KillIcon> icons = EnumSet.noneOf(KillIcon.class);
        if (credit != null && !credit.expired() && credit.attackerId().equals(attacker.getUniqueId())) {
            icons.addAll(credit.icons());
        }
        icons.addAll(killIcons(event.getEntity().getLastDamageCause(), credit));
        if (icons.isEmpty()) {
            icons.add(KillIcon.FALLBACK);
        }

        int streak = rememberKillStreak(attacker);
        attackerPlayer.playSound(Sound.ITEM_TRIDENT_RETURN, 1.3f, 0.35f);
        attackerPlayer.playSound(Sound.ITEM_TRIDENT_RETURN, 1.3f, 1.15f);
        enqueueKillSubtitle(attackerPlayer, target, icons, streak);
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
        damageCredits.put(target.getUniqueId(), credit.refreshed(killIcons(target.getLastDamageCause(), credit)));
    }

    private void rememberDamageCredit(Player target, Player attacker, Set<KillIcon> icons) {
        GameModule game = game(target);
        if (game == null || game(attacker) != game || !allowsKillCredit(game, target, attacker)) {
            return;
        }
        DamageCredit previous = damageCredits.get(target.getUniqueId());
        if (previous != null && previous.attackerId().equals(attacker.getUniqueId()) && !previous.expired()) {
            damageCredits.put(target.getUniqueId(), previous.refreshed(icons));
            return;
        }
        damageCredits.put(target.getUniqueId(), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis(), icons));
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
        blockCredits.put(BlockKey.of(block), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis(), Set.of(KillIcon.BLOCK)));
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
                rememberDamageCredit(target, attacker, Set.of(KillIcon.BLOCK));
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

    private Set<KillIcon> killIcons(EntityDamageEvent event, DamageCredit credit) {
        Set<KillIcon> icons = EnumSet.noneOf(KillIcon.class);
        if (event == null) {
            return icons;
        }
        DamageType damageType = event.getDamageSource().getDamageType();
        if (damageType == DamageType.ARROW || damageType == DamageType.MOB_PROJECTILE) {
            icons.add(KillIcon.BOW);
        } else if (damageType == DamageType.TRIDENT) {
            icons.add(KillIcon.TRIDENT);
        } else if (damageType == DamageType.PLAYER_ATTACK || damageType == DamageType.MOB_ATTACK) {
            icons.add(meleeIcon(event));
        } else if (damageType == DamageType.EXPLOSION || damageType == DamageType.PLAYER_EXPLOSION) {
            icons.add(KillIcon.EXPLOSION);
        } else if (damageType == DamageType.ON_FIRE || damageType == DamageType.IN_FIRE || damageType == DamageType.LAVA || damageType == DamageType.HOT_FLOOR) {
            icons.add(KillIcon.FIRE);
        } else if (damageType == DamageType.MAGIC || damageType == DamageType.INDIRECT_MAGIC) {
            icons.add(KillIcon.POTION);
        } else if (damageType == DamageType.FALL || damageType == DamageType.OUT_OF_WORLD) {
            icons.add(KillIcon.SPLEEF);
        } else if (damageType == DamageType.FREEZE) {
            icons.add(KillIcon.FREEZE);
        } else if (damageType == DamageType.LIGHTNING_BOLT) {
            icons.add(KillIcon.LIGHTNING);
        }
        Entity directEntity = event.getDamageSource().getDirectEntity();
        if (directEntity instanceof Projectile projectile) {
            String projectileType = projectile.getType().name();
            if (projectileType.contains("TRIDENT")) {
                icons.add(KillIcon.TRIDENT);
            } else if (projectileType.contains("ARROW")) {
                icons.add(KillIcon.BOW);
            } else if (projectileType.contains("POTION")) {
                icons.add(KillIcon.POTION);
            }
        }
        if (credit != null && !credit.blockExpired() && (damageType == DamageType.LAVA || damageType == DamageType.ON_FIRE || damageType == DamageType.IN_FIRE)) {
            icons.add(KillIcon.FIRE);
        }
        return icons;
    }

    private KillIcon meleeIcon(EntityDamageEvent event) {
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (!(causingEntity instanceof Player player)) {
            return KillIcon.SWORD;
        }
        Material weapon = player.getInventory().getItemInMainHand().getType();
        if (weapon.name().endsWith("_AXE")) {
            return KillIcon.AXE;
        }
        if (weapon.name().endsWith("_PICKAXE")) {
            return KillIcon.PICKAXE;
        }
        if (weapon == Material.TRIDENT) {
            return KillIcon.TRIDENT;
        }
        return KillIcon.SWORD;
    }

    private int rememberKillStreak(Player attacker) {
        long now = System.currentTimeMillis();
        KillStreak streak = killStreaks.get(attacker.getUniqueId());
        int count = streak == null || now - streak.lastKillMillis() > MULTI_KILL_MILLIS ? 1 : streak.count() + 1;
        killStreaks.put(attacker.getUniqueId(), new KillStreak(count, now));
        killStreaks.entrySet().removeIf(entry -> now - entry.getValue().lastKillMillis() > MULTI_KILL_MILLIS);
        return count;
    }

    private void enqueueKillSubtitle(GamePlayer attackerPlayer, Player target, Set<KillIcon> icons, int streak) {
        Player attacker = attackerPlayer.bukkitPlayer();
        if (attacker == null) {
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        Deque<KillSubtitle> queue = killSubtitles.computeIfAbsent(attackerId, ignored -> new ArrayDeque<>());
        var team = target.getScoreboard().getPlayerTeam(target);
        queue.addLast(new KillSubtitle(killSubtitle(target.getName(), team == null ? NamedTextColor.WHITE : team.color(), target, icons, streak)));
        if (queue.size() == 1) {
            showNextKillSubtitle(attackerPlayer, attackerId);
        }
    }

    private void showNextKillSubtitle(GamePlayer attackerPlayer, UUID attackerId) {
        Deque<KillSubtitle> queue = killSubtitles.get(attackerId);
        if (queue == null || queue.isEmpty()) {
            killSubtitles.remove(attackerId);
            return;
        }
        KillSubtitle subtitle = queue.peekFirst();
        attackerPlayer.subtitle(subtitle.component());
        moduleService.plugin().getServer().getScheduler().runTaskLater(moduleService.plugin(), () -> {
            Deque<KillSubtitle> current = killSubtitles.get(attackerId);
            if (current == null) {
                return;
            }
            current.pollFirst();
            showNextKillSubtitle(attackerPlayer, attackerId);
        }, KILL_SUBTITLE_SPACING_TICKS);
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

    private record DamageCredit(UUID attackerId, long timeMillis, Set<KillIcon> icons) {
        boolean expired() {
            return System.currentTimeMillis() - timeMillis > KILL_CREDIT_MILLIS;
        }

        boolean blockExpired() {
            return System.currentTimeMillis() - timeMillis > BLOCK_CREDIT_MILLIS;
        }

        DamageCredit refreshed() {
            return refreshed(Set.of());
        }

        DamageCredit refreshed(KillIcon icon) {
            return refreshed(Set.of(icon));
        }

        DamageCredit refreshed(Set<KillIcon> newIcons) {
            Set<KillIcon> mergedIcons = icons.isEmpty() ? EnumSet.noneOf(KillIcon.class) : EnumSet.copyOf(icons);
            mergedIcons.addAll(newIcons);
            return new DamageCredit(attackerId, System.currentTimeMillis(), mergedIcons);
        }
    }

    private record KillStreak(int count, long lastKillMillis) {
    }

    private Component killSubtitle(String targetName, TextColor targetColor, Player target, Set<KillIcon> icons, int streak) {
        Component component = Component.empty();
        for (KillIcon icon : icons) {
            component = component.append(Component.text(icon.text()));
        }
        component = component
            .append(Component.text(" "))
            .append(Component.text(targetName + " ", targetColor))
            .append(Component.object(ObjectContents.playerHead(target)));
        if (streak >= 2) {
            component = component.append(Component.text(" x" + streak, NamedTextColor.GOLD));
        }
        return component;
    }

    private record KillSubtitle(Component component) {
    }

    private enum KillIcon {
        TRIDENT("🔱"),
        SWORD("🗡"),
        BOW("🏹"),
        PICKAXE("⛏"),
        SPLEEF("☒"),
        BLOCK("☒"),
        FIRE("🔥"),
        FREEZE("❄"),
        EXPLOSION("☄"),
        POTION("🧪"),
        LIGHTNING("☀"),
        AXE("🪓"),
        FALLBACK("☠");

        private final String text;

        KillIcon(String text) {
            this.text = text;
        }

        String text() {
            return text;
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
