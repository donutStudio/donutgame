// reject: Module API forbids com.donutsforlife11.donutgame.api.map.GameWorldHandle
package fixture;
import com.donutsforlife11.donutgame.api.map.GameWorld;
class InternalBridge {
    void raw(GameWorld world) {
        com.donutsforlife11.donutgame.api.map.GameWorldHandle.setBukkitWorld(world, null);
    }
}
