package com.donutsforlife11.donutgame.api.player;

import org.bukkit.entity.Player;

/**
 * Runtime-only spectator state.
 *
 * This class deliberately does not persist logical spectator state. The
 * GamePlayer instance is the sole authority for whether the player is currently
 * spectating; this object only projects that in-memory state onto an attached
 * Bukkit Player while the player is online.
 */
final class SpectatorSession implements AutoCloseable {
    private final GamePlayer owner;
    private boolean closed;

    private SpectatorSession(GamePlayer owner) {
        this.owner = owner;
    }

    static SpectatorSession open(GamePlayer owner, Player player) {
        SpectatorSession session = new SpectatorSession(owner);
        session.apply(player);
        return session;
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
        detach(player);
    }

    /**
     * Removes spectator-only visibility/list presentation from an attached
     * Bukkit player without changing the logical spectator session. This is
     * used on disconnect while GamePlayer restores the normal Bukkit
     * projection without clearing the in-memory spectator session.
     */
    void detach(Player player) {
        if (player == null) {
            return;
        }
        owner.module().plugin().spectatorService().hideSpectator(owner);
    }

    void apply(Player player) {
        if (closed || player == null || !owner.isOnline() || !owner.uuid().equals(player.getUniqueId())) {
            return;
        }
        // Spectator restrictions are authoritative in memory and enforced by
        // SpectatorGuardEvents/SpectatorService. Do not set Bukkit invulnerability,
        // invisibility, pickup, or game-mode flags that can be serialized to player.dat.
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
        owner.module().plugin().spectatorService().showSpectator(owner);
    }
}
