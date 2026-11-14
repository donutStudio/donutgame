import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Builds scoped delegates from the pinned Paper signatures and explicit exposure rules. */
public abstract class PaperFacadeGenerateTask extends DefaultTask {
    private static final String API = "com.donutsforlife11.donutgame.api.";

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @TaskAction
    void generate() throws Exception {
        var urls = new ArrayList<java.net.URL>();
        for (var file : getClasspath()) {
            urls.add(file.toURI().toURL());
        }
        try (var paper = new URLClassLoader(urls.toArray(java.net.URL[]::new), getClass().getClassLoader())) {
            generate(paper);
        }
    }

    private void generate(ClassLoader paper) throws Exception {
        write("event", "PaperEventMethods", """
            import org.bukkit.block.Block;
            import org.bukkit.entity.Entity;
            import com.donutsforlife11.donutgame.api.entity.GameEntityBase;
            import com.donutsforlife11.donutgame.api.map.GameBlock;
            import com.donutsforlife11.donutgame.api.map.GameWorld;

            abstract class PaperEventMethods {
                abstract <T> T require(Class<T> type);
                abstract GameEntityBase wrapEntity(Entity entity);
                abstract GameBlock wrapBlock(Block block);
                abstract GameWorld scopedWorld();
            """, "public final", discover(paper, List.of(
            expose("event.entity.EntityDamageByEntityEvent", "getDamager"),
            expose("event.entity.EntityDamageEvent", "getDamage", "getFinalDamage", "setDamage"),
            expose("event.entity.EntityTargetEvent", "getTarget"),
            expose("event.player.PlayerInteractEntityEvent", "getRightClicked", "getHand"),
            expose("event.player.PlayerInteractEvent", "getClickedBlock", "getBlockFace"),
            expose("event.entity.ProjectileHitEvent", "getHitEntity", "getHitBlock", "getHitBlockFace"),
            expose("event.player.PlayerMoveEvent", "hasChangedPosition", "hasChangedBlock", "hasChangedOrientation"),
            expose("event.player.PlayerRespawnEvent", "getRespawnLocation"),
            expose("event.entity.EntityDeathEvent", "getDroppedExp", "getDrops"),
            expose("event.entity.PlayerDeathEvent", "getKeepInventory", "getKeepLevel")
        ), spec -> "require(" + spec.type() + ".class)", "wrapEntity", "wrapBlock", "scopedWorld()"));

        write("entity", "PaperEntityMethods", """
            import java.util.List;
            import org.bukkit.entity.Entity;
            import org.bukkit.entity.LivingEntity;
            import org.bukkit.util.Vector;
            import org.bukkit.potion.PotionEffect;
            import net.kyori.adventure.text.Component;
            import com.donutsforlife11.donutgame.api.map.GameLocation;
            import com.donutsforlife11.donutgame.api.map.GameLocationHandle;

            interface PaperEntityMethods {
                private GameEntityBase wrapEntity(Entity target) {
                    return GameEntityHandle.wrap(((GameEntityBase) this).world(), target);
                }

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
            "void setVelocity(Vector velocity) = entity().setVelocity(velocity == null ? new Vector() : velocity.clone())",
            "Vector getVelocity() = entity().getVelocity().clone()",
            "void customName(Component name) = entity().customName(name)",
            "Component customName() = entity().customName()",
            "void clearActivePotionEffects() = living().clearActivePotionEffects()",
            "List<PotionEffect> getActivePotionEffects() = List.copyOf(living().getActivePotionEffects())"
        ), discover(paper, List.of(expose("entity.Entity", "remove", "setCustomNameVisible", "isCustomNameVisible", "setInvulnerable", "isInvulnerable", "setInvisible", "isInvisible", "setGravity", "hasGravity", "setSilent", "isSilent", "setFireTicks", "getFireTicks", "getVehicle", "getPassengers",
            "isInsideVehicle", "isOnGround", "getTicksLived"), expose("entity.LivingEntity", "getHealth", "damage", "swingMainHand", "swingOffHand", "addPotionEffect", "removePotionEffect", "hasPotionEffect")), spec -> spec.type().endsWith(".LivingEntity") ? "living()" : "entity()", "wrapEntity", null, null));
        write("item", "PaperItemMethods", """
            import org.bukkit.inventory.ItemStack;

            abstract class PaperItemMethods {
                abstract ItemStack bukkitItem();
            """, "public final", discover(paper, List.of(expose("inventory.ItemStack", "getType", "getAmount", "getMaxStackSize", "addEnchantment", "addUnsafeEnchantment", "getItemMeta", "setItemMeta", "isEmpty", "hasItemMeta",
            "containsEnchantment", "getEnchantmentLevel")), spec -> "bukkitItem()", null, null, null));
        write("map", "PaperBlockMethods", """
            import java.util.Objects;
            import org.bukkit.Material;
            import org.bukkit.block.Block;
            import org.bukkit.block.data.BlockData;

            abstract class PaperBlockMethods {
                abstract Block block();
            """, "public final", List.of(
            "BlockData getBlockData() = block().getBlockData().clone()",
            "void setType(Material material) = block().setType(Objects.requireNonNull(material, \"material\"), false)",
            "void setBlockData(BlockData data) = block().setBlockData(Objects.requireNonNull(data, \"data\").clone(), false)"
        ), discover(paper, List.of(expose("block.Block", "getType", "isEmpty", "isLiquid", "getLightLevel", "getLightFromSky",
            "getLightFromBlocks", "isPassable")), spec -> "block()", null, null, null));
        write("map", "PaperWorldMethods", """
            import org.bukkit.World;

            abstract class PaperWorldMethods {
                abstract World requireWorld();
            """, "public final", discover(paper, List.of(expose("World", "getMinHeight", "getMaxHeight", "getTime", "setTime", "getDifficulty", "setDifficulty", "getFullTime", "getSeaLevel")),
            spec -> "requireWorld()", null, null, null));
    }

    // Each rule chooses a Paper family and useful method names. Overloads are
    // exposed only when all arguments and the result have explicit mappings.
    private record Exposure(String type, List<String> names) {}

    private static Exposure expose(String type, String... names) {
        return new Exposure("org.bukkit." + type, List.of(names));
    }

    private static final Set<String> VALUES = Set.of(
        "org.bukkit.inventory.EquipmentSlot", "org.bukkit.block.BlockFace",
        "org.bukkit.enchantments.Enchantment", "org.bukkit.Material", "org.bukkit.Difficulty",
        "org.bukkit.inventory.meta.ItemMeta", "org.bukkit.potion.PotionEffect",
        "org.bukkit.potion.PotionEffectType"
    );

    private static String valueType(Type type) {
        if (type instanceof Class<?> cls && (cls.isPrimitive() || VALUES.contains(cls.getName()))) {
            return cls.getCanonicalName();
        }
        return null;
    }

    private record Result(String type, String expression) {}

    private static Result result(Type type, String call, String entity, String block, String world) {
        String value = valueType(type);
        if (value != null) {
            return new Result(value, call);
        }
        String name = type.getTypeName();
        if (entity != null && name.equals("org.bukkit.entity.Entity")) {
            return new Result(API + "entity.GameEntityBase", entity + "(" + call + ")");
        }
        if (block != null && name.equals("org.bukkit.block.Block")) {
            return new Result(API + "map.GameBlock", block + "(" + call + ")");
        }
        if (world != null && name.equals("org.bukkit.Location")) {
            return new Result(API + "map.GameLocation", API + "map.GameLocationHandle.create(" + world + ", " + call + ")");
        }
        if (world != null && name.equals("org.bukkit.inventory.ItemStack")) {
            return new Result(API + "item.GameItem", API + "item.GameItem.from(" + call + ")");
        }
        if (type instanceof ParameterizedType list && list.getRawType() == List.class) {
            Result element = result(list.getActualTypeArguments()[0], "element", entity, block, world);
            if (element != null) {
                return new Result("java.util.List<" + element.type() + ">", call
                    + ".stream().map(element -> " + element.expression() + ").filter(java.util.Objects::nonNull).toList()");
            }
        }
        return null;
    }

    private static List<String> discover(ClassLoader paper, List<Exposure> exposures,
                                         java.util.function.Function<Exposure, String> receiver,
                                         String entity, String block, String world) throws ClassNotFoundException {
        Map<String, String> methods = new LinkedHashMap<>();
        for (Exposure exposure : exposures) {
            Method[] available = Class.forName(exposure.type(), false, paper).getMethods();
            for (String name : exposure.names()) {
                boolean found = false;
                for (Method method : java.util.Arrays.stream(available).sorted(java.util.Comparator.comparing(Method::toGenericString)).toList()) {
                    if (!method.getName().equals(name) || method.isAnnotationPresent(Deprecated.class) || method.isBridge() || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    var parameters = new ArrayList<String>();
                    var arguments = new ArrayList<String>();
                    for (Type parameter : method.getGenericParameterTypes()) {
                        String mapped = valueType(parameter);
                        if (mapped == null) {
                            break;
                        }
                        String argument = "arg" + arguments.size();
                        parameters.add(mapped + " " + argument);
                        arguments.add(argument);
                    }
                    if (parameters.size() != method.getParameterCount()) {
                        continue;
                    }
                    String call = receiver.apply(exposure) + "." + name + "(" + String.join(", ", arguments) + ")";
                    Result result = result(method.getGenericReturnType(), call, entity, block, world);
                    if (result == null) {
                        continue;
                    }
                    String signature = name + "(" + String.join(", ", parameters) + ")";
                    String delegate = result.type() + " " + signature + " = " + result.expression();
                    if (methods.putIfAbsent(signature, delegate) != null) {
                        throw new IllegalStateException("Conflicting Paper delegates for " + signature);
                    }
                    found = true;
                }
                if (!found) {
                    throw new IllegalStateException("No supported Paper signature for " + exposure.type() + "." + name);
                }
            }
        }
        return List.copyOf(methods.values());
    }

    @SafeVarargs
    private final void write(String pkg, String name, String declaration, String modifier, List<String> methods, List<String>... discovered) throws IOException {
        var java = new StringBuilder("// Generated by PaperFacadeGenerateTask.\npackage " + API + pkg + ";\n\n");
        java.append(declaration);
        var delegates = new ArrayList<>(methods);
        for (List<String> additional : discovered) {
            delegates.addAll(additional);
        }
        for (String method : delegates) {
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
