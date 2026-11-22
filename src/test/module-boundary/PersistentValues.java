// allow
package fixture;
import java.util.List;
import com.donutsforlife11.donutgame.api.storage.GameStorage;
import com.donutsforlife11.donutgame.api.player.GamePlayer;
import com.donutsforlife11.donutgame.api.item.GameItem;
class PersistentValues {
    record Progress(int level, List<GameItem> items) {}
    void save(GameStorage storage, GamePlayer player, Progress progress) {
        storage.player(player).set("progress", progress);
        Progress restored = storage.player(player).get("progress", Progress.class);
    }
}
