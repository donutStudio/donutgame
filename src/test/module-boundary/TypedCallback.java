// reject: Module API forbids org.bukkit.entity.Player
package fixture;
import java.util.function.Consumer;
class TypedCallback {
    Object raw() { return (Consumer<org.bukkit.entity.Player>) (org.bukkit.entity.Player p) -> {}; }
}
