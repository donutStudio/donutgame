// reject: method getAdapterRegistry()
package fixture;
import org.bukkit.event.player.PlayerMoveEvent;
import com.donutsforlife11.donutgame.api.event.GameEvent;
class EventAdapter {
    Object escape(GameEvent<PlayerMoveEvent> event) { return event.getAdapterRegistry(); }
}
