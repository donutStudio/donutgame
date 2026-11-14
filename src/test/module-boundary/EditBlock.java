// reject: private access
package fixture;
import org.bukkit.block.BlockState;
import com.donutsforlife11.donutgame.api.map.GameBlock;
class EditBlock {
    void edit(GameBlock block) { block.editState(BlockState.class, raw -> {}); }
}
