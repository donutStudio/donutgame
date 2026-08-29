package com.donutsforlife11.donutgame.api.event;

import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.team.GameTeam;
import com.donutsforlife11.donutgame.internal.game.GameModule;

public class GamePlayerDeathEvent extends GamePlayerEvent<PlayerDeathEvent> {
    GamePlayerDeathEvent(PlayerDeathEvent event, GamePlayer player, GameModule module) {
        super(event, player, module);
    }

    public GameEntity getDamager() {
        DamageSource source = event().getDamageSource();
        GameEntity causingEntity = gameEntity(source.getCausingEntity());
        if (causingEntity != null) return deathSafeDamager(causingEntity);
        GameEntity directEntity = gameEntity(source.getDirectEntity());
        if (directEntity != null) return deathSafeDamager(directEntity);
        return deathSafeDamager(gamePlayer(event().getEntity().getKiller()));
    }

    public void setDropItems(boolean dropItems) {
        event().setDroppedExp(dropItems ? event().getDroppedExp() : 0);
        event().setKeepInventory(!dropItems);
    }

    public void clearDrops() {
        event().getDrops().clear();
    }

    public void setDroppedExp(int droppedExp) {
        event().setDroppedExp(droppedExp);
    }

    public void setKeepLevel(boolean keepLevel) {
        event().setKeepLevel(keepLevel);
    }

    public void setKeepInventory(boolean keepInventory) {
        event().setKeepInventory(keepInventory);
    }

    public void setRespawnLocation(GameLocation location) {
        if (location != null) getPlayer().setSpawnPoint(location);
    }

    private GameEntity deathSafeDamager(GameEntity candidate) {
        if (!(candidate instanceof GamePlayer attacker)) return candidate;
        GamePlayer target = getPlayer();
        if (target == null || target == attacker) return null;
        GameTeam team = attacker.team();
        return team != null && team == target.team() && !team.friendlyFire() ? null : candidate;
    }
}
