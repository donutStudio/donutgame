// reject: Module API forbids org.bukkit.event.player.PlayerMoveEvent except as a GameEvent type token
package fixture;
import org.bukkit.event.player.PlayerMoveEvent;
class RawEvent {
    void raw(PlayerMoveEvent event) { event.setCancelled(false); }
}
