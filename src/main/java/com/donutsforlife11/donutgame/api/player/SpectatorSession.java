package com.donutsforlife11.donutgame.api.player;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

final class SpectatorSession implements AutoCloseable {
    private static final String MARKER_KEY = "spectator_session";

    private final GamePlayer owner;
    private final UUID id = UUID.randomUUID();
    private final Baseline baseline;
    private boolean closed;

    private SpectatorSession(GamePlayer owner, Player player) {
        this.owner = owner;
        this.baseline = Baseline.capture(player);
    }

    static SpectatorSession open(GamePlayer owner, Player player) {
        SpectatorSession session = new SpectatorSession(owner, player);
        if (player != null) {
            session.apply(player);
        }
        return session;
    }

    static boolean hasMarker(GamePlayer owner, Player player) {
        return player != null
            && player.getPersistentDataContainer().has(markerKey(owner), PersistentDataType.STRING);
    }

    @Override
    public void close() {
        close(owner.bukkitPlayer());
    }

    void close(Player player) {
        if (closed) {
            return;
        }
        closed = true;
        if (player == null) {
            return;
        }
        owner.module().plugin().spectatorService().hideSpectator(owner);
        baseline.restore(player);
        clearMarker(owner, player);
        player.updateInventory();
    }

    void apply(Player player) {
        if (closed || player == null) {
            return;
        }
        player.getPersistentDataContainer().set(markerKey(owner), PersistentDataType.STRING, id.toString());
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCanPickupItems(false);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20));
        player.updateInventory();
        owner.module().plugin().spectatorService().showSpectator(owner);
    }

    private static void clearMarker(GamePlayer owner, Player player) {
        player.getPersistentDataContainer().remove(markerKey(owner));
    }

    private static NamespacedKey markerKey(GamePlayer owner) {
        return new NamespacedKey(owner.module().plugin(), MARKER_KEY);
    }

    private record Baseline(
        GameMode gameMode,
        boolean invulnerable,
        boolean invisible,
        boolean canPickupItems,
        boolean allowFlight,
        boolean flying,
        int fireTicks,
        int freezeTicks,
        float fallDistance,
        int noDamageTicks
    ) {
        static Baseline capture(Player player) {
            if (player == null) {
                return new Baseline(GameMode.SURVIVAL, false, false, true, false, false, 0, 0, 0.0f, 0);
            }
            return new Baseline(
                player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode(),
                player.isInvulnerable(),
                player.isInvisible(),
                player.getCanPickupItems(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFireTicks(),
                player.getFreezeTicks(),
                player.getFallDistance(),
                player.getNoDamageTicks()
            );
        }

        void restore(Player player) {
            player.setGameMode(gameMode);
            player.setInvulnerable(invulnerable);
            player.setInvisible(invisible);
            player.setCanPickupItems(canPickupItems);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setFireTicks(fireTicks);
            player.setFreezeTicks(freezeTicks);
            player.setFallDistance(fallDistance);
            player.setNoDamageTicks(noDamageTicks);
        }
    }
}
