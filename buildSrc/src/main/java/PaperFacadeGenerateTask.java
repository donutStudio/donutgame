import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** The signatures below are the whole delegation policy. Javac checks the calls against pinned Paper. */
public abstract class PaperFacadeGenerateTask extends DefaultTask {
    private static final String API = "com.donutsforlife11.donutgame.api.";

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    void generate() throws IOException {
        write("entity", "PaperEntityMethods", """
            import java.util.List;
            import org.bukkit.entity.Entity;
            import org.bukkit.entity.LivingEntity;
            import org.bukkit.util.Vector;
            import org.bukkit.potion.PotionEffect;
            import org.bukkit.potion.PotionEffectType;
            import net.kyori.adventure.text.Component;
            import com.donutsforlife11.donutgame.api.map.GameLocation;
            import com.donutsforlife11.donutgame.api.map.GameLocationHandle;

            interface PaperEntityMethods {
                private Entity entity() {
                    return GameEntityHandle.requireBukkitEntity((GameEntityBase) this);
                }

                private LivingEntity living() {
                    if (entity() instanceof LivingEntity actor) {
                        return actor;
                    }
                    throw new IllegalStateException("This operation requires a living entity.");
                }
            """, "default", List.of(
            "GameLocation getLocation() = GameLocationHandle.create(((GameEntityBase) this).world(), entity().getLocation())",
            "void remove() = entity().remove()",
            "double getHealth() = living().getHealth()",
            "void damage(double amount) = living().damage(amount)",
            "void swingMainHand() = living().swingMainHand()",
            "void swingOffHand() = living().swingOffHand()",
            "void setVelocity(Vector velocity) = entity().setVelocity(velocity == null ? new Vector() : velocity.clone())",
            "Vector getVelocity() = entity().getVelocity().clone()",
            "void customName(Component name) = entity().customName(name)",
            "Component customName() = entity().customName()",
            "void setCustomNameVisible(boolean visible) = entity().setCustomNameVisible(visible)",
            "boolean isCustomNameVisible() = entity().isCustomNameVisible()",
            "void setInvulnerable(boolean invulnerable) = entity().setInvulnerable(invulnerable)",
            "boolean isInvulnerable() = entity().isInvulnerable()",
            "void setInvisible(boolean invisible) = entity().setInvisible(invisible)",
            "boolean isInvisible() = entity().isInvisible()",
            "void setGravity(boolean gravity) = entity().setGravity(gravity)",
            "boolean hasGravity() = entity().hasGravity()",
            "void setSilent(boolean silent) = entity().setSilent(silent)",
            "boolean isSilent() = entity().isSilent()",
            "void setFireTicks(int ticks) = entity().setFireTicks(ticks)",
            "int getFireTicks() = entity().getFireTicks()",
            "boolean addPotionEffect(PotionEffect effect) = living().addPotionEffect(effect)",
            "void clearActivePotionEffects() = living().clearActivePotionEffects()",
            "void removePotionEffect(PotionEffectType effect) = living().removePotionEffect(effect)",
            "boolean hasPotionEffect(PotionEffectType effect) = living().hasPotionEffect(effect)",
            "List<PotionEffect> getActivePotionEffects() = List.copyOf(living().getActivePotionEffects())"
        ));
        write("item", "PaperItemMethods", """
            import org.bukkit.Material;
            import org.bukkit.enchantments.Enchantment;
            import org.bukkit.inventory.ItemStack;
            import org.bukkit.inventory.meta.ItemMeta;

            abstract class PaperItemMethods {
                abstract ItemStack bukkitItem();
            """, "public final", List.of(
            "Material getType() = bukkitItem().getType()",
            "int getAmount() = bukkitItem().getAmount()",
            "int getMaxStackSize() = bukkitItem().getMaxStackSize()",
            "void addEnchantment(Enchantment enchantment, int level) = bukkitItem().addEnchantment(enchantment, level)",
            "void addUnsafeEnchantment(Enchantment enchantment, int level) = bukkitItem().addUnsafeEnchantment(enchantment, level)",
            "ItemMeta getItemMeta() = bukkitItem().getItemMeta()",
            "boolean setItemMeta(ItemMeta meta) = bukkitItem().setItemMeta(meta)"
        ));
        write("map", "PaperBlockMethods", """
            import java.util.Objects;
            import org.bukkit.Material;
            import org.bukkit.block.Block;
            import org.bukkit.block.data.BlockData;

            abstract class PaperBlockMethods {
                abstract Block block();
            """, "public final", List.of(
            "Material getType() = block().getType()",
            "BlockData getBlockData() = block().getBlockData().clone()",
            "boolean isEmpty() = block().isEmpty()",
            "boolean isLiquid() = block().isLiquid()",
            "void setType(Material material) = block().setType(Objects.requireNonNull(material, \"material\"), false)",
            "void setBlockData(BlockData data) = block().setBlockData(Objects.requireNonNull(data, \"data\").clone(), false)"
        ));
        write("map", "PaperWorldMethods", """
            import org.bukkit.Difficulty;
            import org.bukkit.World;

            abstract class PaperWorldMethods {
                abstract World requireWorld();
            """, "public final", List.of(
            "int getMinHeight() = requireWorld().getMinHeight()",
            "int getMaxHeight() = requireWorld().getMaxHeight()",
            "long getTime() = requireWorld().getTime()",
            "void setTime(long time) = requireWorld().setTime(time)",
            "Difficulty getDifficulty() = requireWorld().getDifficulty()",
            "void setDifficulty(Difficulty difficulty) = requireWorld().setDifficulty(difficulty)"
        ));
    }

    private void write(String pkg, String name, String declaration, String modifier, List<String> methods) throws IOException {
        var java = new StringBuilder("// Generated by PaperFacadeGenerateTask.\npackage " + API + pkg + ";\n\n");
        java.append(declaration);
        for (String method : methods) {
            String[] parts = method.split(" = ", 2);
            java.append("\n    ").append(modifier).append(' ').append(parts[0]).append(" {\n        ");
            if (!parts[0].startsWith("void ")) {
                java.append("return ");
            }
            java.append(parts[1]).append(";\n    }\n");
        }
        Path file = getOutputDirectory().get().getAsFile().toPath()
            .resolve((API + pkg).replace('.', '/')).resolve(name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, java.append("}\n"), StandardCharsets.UTF_8);
    }
}
