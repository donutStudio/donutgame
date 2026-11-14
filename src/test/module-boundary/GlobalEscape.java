// reject: Module API forbids org.bukkit.Bukkit
package fixture;
class GlobalEscape {
    Object raw() { return org.bukkit.Bukkit.getWorld("other"); }
}
