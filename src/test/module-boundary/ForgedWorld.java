// reject: Module API forbids constructing com.donutsforlife11.donutgame.api.map.GameWorld
package fixture;
import com.donutsforlife11.donutgame.internal.game.GameModule;
import com.donutsforlife11.donutgame.api.map.GameWorld;
class ForgedWorld extends GameModule {
    Object forge() { return new GameWorld(this); }
}
