// reject: Module API forbids com.donutsforlife11.donutgame.api.entity.GameEntityHandle
package fixture;
import com.donutsforlife11.donutgame.api.entity.GameEntity;
import static com.donutsforlife11.donutgame.api.entity.GameEntityHandle.bukkitEntity;
class HandleEscape {
    Object raw(GameEntity entity) { return bukkitEntity(entity); }
}
