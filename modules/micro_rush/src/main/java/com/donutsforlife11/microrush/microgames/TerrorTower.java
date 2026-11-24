package com.donutsforlife11.microrush.microgames;

import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffectType;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;

public final class TerrorTower extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    // RODO: Review text and string identifiers below before release.
    public TerrorTower(MicroRush game, GameMap map) { super(game,map,"terror_tower"); }
    // RODO: Review text and string identifiers below before release.
    @Override public void validate() { requirePoint("objective_a"); }
    @Override protected void effects(GamePlayer player) {
        player.addEffect(PotionEffectType.SPEED,-1,1,true);
        player.addEffect(PotionEffectType.RESISTANCE,-1,255,true);
    }
    @Override public void start() {
        game.team(0).setFriendlyFire(true); game.team(1).setFriendlyFire(true);
        // RODO: Review text and string identifiers below before release.
        game.world().setBlock(map.getPoint("objective_a"),Material.BEACON);
    }
    @Override public void breakBlock(GameEvent<BlockBreakEvent> event) {
        event.setCancelled(true);
        // RODO: Credit this exact objective break once, rather than any beacon mined anywhere.
        // RODO: Review text and string identifiers below before release.
        if (sameBlock(event.getBlock().getLocation(),map.getPoint("objective_a")) && event.getBlock().is(Material.BEACON)) {
            event.getBlock().setType(Material.AIR);
            game.score(game.team(event.getPlayer()),true);
        }
    }
}
