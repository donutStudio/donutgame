package com.donutsforlife11.microrush.microgames;

import org.bukkit.Material;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;

public final class SniperShowdown extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    // RODO: Review text and string identifiers below before release.
    public SniperShowdown(MicroRush game, GameMap map) { super(game,map,"sniper_showdown"); }
    @Override protected void effects(GamePlayer player) {  }
    @Override public void start() { game.team(0).setFriendlyFire(false); game.team(1).setFriendlyFire(false); }
    @Override public void lateJoin(GamePlayer player) { player.setSpectator(true,spawn(game.team(player))); }
    @Override public void death(GamePlayer player) { after(1,this::checkTeams); }
    @Override public void quit(GamePlayer player) { after(1,this::checkTeams); }
    private void checkTeams() {

        boolean red = game.team(0).getNonSpectators().stream().anyMatch(GamePlayer::isOnline);
        boolean blue = game.team(1).getNonSpectators().stream().anyMatch(GamePlayer::isOnline);
        // RODO: Simultaneous eliminations replay the point. Every new point restores health, kits and ammunition.
        if (!red && !blue && game.soloTesting()) game.score(game.team(0).getPlayers().isEmpty() ? 0 : 1, true);
        else if (!red && !blue) game.restartPoint();
        else if (!red) game.score(1,true);
        else if (!blue) game.score(0,true);
    }
    @Override public void move(GameEvent<PlayerMoveEvent> event) {
        super.move(event);
        if (!active()) return;
        if (otherSide(event.getPlayer(),event.getTo())) { event.setTo(event.getFrom()); return; }
        if (water(event.getTo())) event.getPlayer().kill();
    }
    @Override public void damage(GameEvent<EntityDamageEvent> event) {
        if (event.getEntity() instanceof GamePlayer && event.getAttacker() != null
            && event.getDamager() instanceof com.donutsforlife11.donutgame.api.entity.GameEntity projectile
            && (projectile.getType() == org.bukkit.entity.EntityType.ARROW || projectile.getType() == org.bukkit.entity.EntityType.SPECTRAL_ARROW)) event.setDamage(1000);
    }
    @Override public void breakBlock(GameEvent<BlockBreakEvent> event) {
        // RODO: Review text and string identifiers below before release.
        event.setCancelled(!event.getBlock().getType().name().endsWith("_WOOL"));
    }
    @Override public void placeBlock(GameEvent<BlockPlaceEvent> event) {
        // RODO: Review text and string identifiers below before release.
        event.setCancelled(!map.getRegion("arena").contains(event.getBlockPlaced().getLocation()) || otherSide(event.getPlayer(),event.getBlockPlaced().getLocation()));
    }
    @Override public void projectile(GameEvent<ProjectileHitEvent> event) {
        var block = event.getHitBlock();
        // RODO: Review text and string identifiers below before release.
        if (block != null && block.getType().name().endsWith("_WOOL")) {
            block.setType(Material.AIR);
            event.getEntity().remove();
        }
    }
}
