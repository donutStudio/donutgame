// reject: method plugin()
package fixture;
import com.donutsforlife11.donutgame.internal.game.GameModule;
class PluginEscape extends GameModule {
    Object raw() { return plugin().getServer(); }
}
