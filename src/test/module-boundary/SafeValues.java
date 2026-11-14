// allow
package fixture;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.donutsforlife11.donutgame.api.event.GameEvent;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
import com.donutsforlife11.donutgame.api.map.GameLocation;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
class SafeValues {
    GameLocation move(GameEntity actor, GameEvent<PlayerMoveEvent> event) {
        Sound sound = Sound.UI_BUTTON_CLICK;
        Material block = Material.STONE;
        actor.setVelocity(new Vector(0, 1, 0));
        event.setTo(actor.getLocation());
        Class<?> token = PlayerMoveEvent.class;
        return event.getTo();
    }

    void damage(GameEvent<EntityDamageByEntityEvent> event) {
        if (event.getDamager() instanceof GamePlayer attacker) {
            event.setDamage(event.getDamage() * 0.5);
            java.util.List<GameEntityBase> passengers = attacker.getPassengers();
        }
    }
}
