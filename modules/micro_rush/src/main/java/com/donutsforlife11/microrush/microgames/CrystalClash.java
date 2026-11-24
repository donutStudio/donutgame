package com.donutsforlife11.microrush.microgames;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.map.GameMap;
import com.donutsforlife11.donutgame.api.object.GameEntities;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.microrush.*;
import net.kyori.adventure.text.Component;

public final class CrystalClash extends Microgame {
    @Override public boolean beginOvertime() { return true; }
    private final Map<UUID,Integer> crystals = new HashMap<>();
    private final Map<UUID,GameEntity> entities = new HashMap<>();
    private boolean fighting;
    private final Map<com.donutsforlife11.donutgame.api.map.GameLocation,org.bukkit.block.data.BlockData> barrierBlocks = new HashMap<>();
    private boolean otherSide(GamePlayer player,com.donutsforlife11.donutgame.api.map.GameLocation at) {
        var own=spawn(game.team(player));var enemy=spawn(1-game.team(player));
        return distance(at,enemy)<distance(at,own);
    }
    private void clearBarrier() {
        barrierBlocks.forEach((at,data) -> game.world().setBlock(at,data));barrierBlocks.clear();
    }
    @Override public void stop() { clearBarrier();super.stop(); }

    // TODO: Review text and string identifiers below before release.
    public CrystalClash(MicroRush game, GameMap map) { super(game,map,"crystal_clash"); }
    // TODO: Review text and string identifiers below before release.
    @Override public void validate() { requirePoint("objective_a"); requirePoint("objective_b"); }
    @Override public void start() {
        game.team(0).setFriendlyFire(false); game.team(1).setFriendlyFire(false);
        game.world().gamerules().pvp(false);
        // TODO: Review text and string identifiers below before release.
        int defense = Math.max(0,game.config().getInt("microgames.crystal_clash.defense_time",30));
        // TODO: Review text and string identifiers below before release.
        game.uiManager().gameMessage(game.playerManager().getPlayers(),Component.text(defense + " seconds to build your defenses!"));
        // TODO: Review text and string identifiers below before release.
        for (String barrier : List.of("objective_c","objective_d")) {
            var regions = new ArrayList<>(map.getRegions(barrier));
            var points=map.getPoints(barrier);
            if(regions.isEmpty() && points.size()>=2) {
                var first=points.getFirst();var last=points.getLast();
                regions.add(new com.donutsforlife11.donutgame.api.map.GameRegion(first,new com.donutsforlife11.donutgame.api.map.GameLocation(last.world(),last.x(),Math.max(first.y(),last.y())+5,last.z())));
            }
            for(var region : regions) for(int x=region.min().blockX();x<=region.max().blockX();x++) for(int y=region.min().blockY();y<=region.max().blockY();y++) for(int z=region.min().blockZ();z<=region.max().blockZ();z++) {
                var at=new com.donutsforlife11.donutgame.api.map.GameLocation(region.world(),x,y,z);
                var block=game.world().blockAt(at);
                if(!block.getType().isAir()) continue;
                barrierBlocks.put(at,block.getBlockData());
                block.setType(barrier.equals("objective_c") ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS);
            }
        }
        after(defense*20, () -> {
            fighting = true;
            // TODO: Review text and string identifiers below before release.
            clearBarrier();
            game.world().gamerules().pvp(true);
            // TODO: Review text and string identifiers below before release.
            game.uiManager().gameMessage(game.playerManager().getPlayers(),Component.text("Defense period is now over! Fight!"));
            for (int team = 0; team < 2; team++) regenerate(team);
        });
    }
    private void regenerate(int team) {
        // TODO: Review text and string identifiers below before release.
        for (var point : map.getPoints(team==0 ? "objective_a" : "objective_b")) {
            GameEntity crystal = game.world().summon(center(point),GameEntities.END_CRYSTAL);
            crystal.setInvulnerable(true); crystal.setGlowing(true);
            crystal.setShowingBottom(false);
            track(crystal); crystals.put(crystal.uuid(),team); entities.put(crystal.uuid(),crystal);
        }
    }
    private void destroy(UUID id, GamePlayer player) {
        Integer owner = crystals.get(id);
        if (!fighting || owner == null || player == null || game.team(player) < 0 || owner == game.team(player)) return;
        crystals.remove(id);
        GameEntity crystal = entities.remove(id);
        game.world().particle(Particle.FIREWORK,crystal.getLocation(),30);
        crystal.remove();
        game.score(game.team(player),false);
        // TODO: Regenerate only an exhausted side, allowing configurable point targets beyond the map's crystal count.
        if (active() && crystals.values().stream().noneMatch(team -> team.equals(owner))) after(40, () -> regenerate(owner));
    }
    @Override public void attack(GameEvent<PrePlayerAttackEntityEvent> event) {
        if (crystals.containsKey(event.getAttacked().uuid())) { event.setCancelled(true); destroy(event.getAttacked().uuid(),event.getPlayer()); }
    }
    @Override public void damage(GameEvent<EntityDamageEvent> event) {
        if (crystals.containsKey(event.getEntity().uuid())) { event.setCancelled(true); destroy(event.getEntity().uuid(),event.getAttacker()); }
        else if (!fighting) event.setCancelled(true);
    }
    @Override public void projectile(GameEvent<ProjectileHitEvent> event) {
        if (event.getHitEntity() != null && crystals.containsKey(event.getHitEntity().uuid())) {
            event.setCancelled(true);
            destroy(event.getHitEntity().uuid(),event.getShooter());
            event.getEntity().remove();
        }
    }
    @Override public void move(GameEvent<PlayerMoveEvent> event) {
        super.move(event);
        if (!fighting && otherSide(event.getPlayer(),event.getTo())) event.setTo(event.getFrom());
    }
    @Override public void breakBlock(GameEvent<BlockBreakEvent> event) {
        String name = event.getBlock().getType().name();
        // TODO: Review text and string identifiers below before release.
        event.setCancelled(!name.endsWith("_WOOL") && !name.equals("COBWEB"));
    }
    @Override public void placeBlock(GameEvent<BlockPlaceEvent> event) {
        // TODO: Review text and string identifiers below before release.
        event.setCancelled(!map.getRegion("arena").contains(event.getBlockPlaced().getLocation()) || otherSide(event.getPlayer(),event.getBlockPlaced().getLocation()));
    }
}
