package com.donutsforlife11.microrush.microgames;

import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;

public final class KingKongKombo extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    // RODO: Review text and string identifiers below before release.
    public KingKongKombo(MicroRush game, GameMap map) { super(game,map,"king_kong_kombo"); }
    // RODO: Review text and string identifiers below before release.
    @Override public void validate() { requirePoint("objective_a"); requirePoint("objective_b"); }
    @Override protected void effects(GamePlayer player) {
        player.addEffect(PotionEffectType.SPEED,-1,1,true);
        player.addEffect(PotionEffectType.RESISTANCE,-1,255,true);
    }
    @Override public void start() { game.team(0).setFriendlyFire(true); game.team(1).setFriendlyFire(true); }
    @Override public void move(GameEvent<PlayerMoveEvent> event) {
        super.move(event);
        int team = game.team(event.getPlayer());
        // RODO: Review text and string identifiers below before release.
        var goal = map.getPoint(team==0 ? "objective_b" : "objective_a");
        if (distance(event.getTo(),goal) <= 1.4*1.4) game.score(team,true);
        else if (water(event.getTo())) event.getPlayer().kill();
    }
}
