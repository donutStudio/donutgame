// reject: Module API forbids org.bukkit.entity.Entity
package fixture;
import java.util.function.Consumer;
class NestedHandle {
    Consumer<? super org.bukkit.entity.Entity> raw() { return ignored -> {}; }
}
