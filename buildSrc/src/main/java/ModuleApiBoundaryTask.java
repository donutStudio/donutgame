import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Uses javac attribution so aliases, inferred results and generic arguments are checked in context. */
public abstract class ModuleApiBoundaryTask extends DefaultTask {
    private static final String FRAMEWORK = "com.donutsforlife11.donutgame";
    private static final String API = FRAMEWORK + ".api.";
    private static final String GAME_MODULE = FRAMEWORK + ".internal.game.GameModule";
    private static final String GAME_EVENT = API + "event.GameEvent";
    private static final String PREFIX = "Module API forbids ";

    // Exact policies for Paper types used as detached data, plus nested types of these values.
    private static final Set<String> VALUES = Set.of(
        "org.bukkit.Color", "org.bukkit.Difficulty", "org.bukkit.GameMode", "org.bukkit.GameRule",
        "org.bukkit.Keyed", "org.bukkit.Material", "org.bukkit.NamespacedKey", "org.bukkit.Particle",
        "org.bukkit.scoreboard.Team.OptionStatus", "org.bukkit.Sound", "org.bukkit.SoundCategory", "org.bukkit.Axis", "org.bukkit.DyeColor",
        "org.bukkit.WeatherType", "org.bukkit.attribute.Attribute", "org.bukkit.attribute.AttributeModifier",
        "org.bukkit.block.BlockFace", "org.bukkit.block.data.BlockData", "org.bukkit.damage.DamageType",
        "org.bukkit.configuration.ConfigurationSection", "org.bukkit.configuration.MemorySection",
        "org.bukkit.configuration.MemoryConfiguration", "org.bukkit.configuration.file.FileConfiguration",
        "org.bukkit.configuration.file.YamlConfiguration", "org.bukkit.enchantments.Enchantment",
        "org.bukkit.entity.EntityType", "org.bukkit.entity.Pose", "org.bukkit.event.EventPriority",
        "org.bukkit.inventory.EquipmentSlot", "org.bukkit.inventory.EquipmentSlotGroup",
        "org.bukkit.inventory.ItemStack", "org.bukkit.inventory.meta.ItemMeta",
        "org.bukkit.potion.PotionEffect", "org.bukkit.potion.PotionEffectType", "org.bukkit.util.Vector",
        "org.bukkit.util.BlockVector", "org.bukkit.util.BoundingBox",
        "com.destroystokyo.paper.profile.PlayerProfile", "com.destroystokyo.paper.profile.ProfileProperty",
        "io.papermc.paper.datacomponent.DataComponentType", "io.papermc.paper.datacomponent.DataComponentTypes",
        "io.papermc.paper.datacomponent.DataComponentBuilder"
    );

    private static final Set<String> INTERNAL_API = Set.of(
        API + "event.GameEventRegistrar", API + "entity.npc.GameNpcService",
        API + "item.ItemNotation", API + "player.PlayerAttributeDefaults",
        API + "ui.title.TitlePacketTracker"
    );

    private static final Set<String> FRAMEWORK_CONSTRUCTORS = Set.of(
        GAME_EVENT, API + "event.GamePlayerLateJoinEvent", API + "entity.GameEntity",
        API + "entity.npc.GameNpc", API + "item.GameLootTable", API + "map.GameWorld", API + "map.MapManager",
        API + "player.PlayerManager", API + "team.TeamManager", API + "ui.UIManager",
        API + "time.TimeManager", API + "border.BorderManager", API + "data.GameData", API + "data.GameDataBase",
        API + "storage.GameStorage"
    );

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputDirectory @Optional @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFixtures();

    @TaskAction
    void verify() throws IOException {
        if (getFixtures().isPresent()) {
            File folder = getFixtures().get().getAsFile();
            List<File> fixtures;
            try (var files = Files.list(folder.toPath())) {
                fixtures = files.filter(path -> path.toString().endsWith(".java")).sorted().map(Path::toFile).toList();
            }
            for (File fixture : fixtures) {
                String directive = Files.readAllLines(fixture.toPath(), StandardCharsets.UTF_8).getFirst();
                List<String> diagnostics = analyze(List.of(fixture));
                if (directive.equals("// allow") ? !diagnostics.isEmpty()
                    : diagnostics.stream().noneMatch(d -> d.contains(directive.substring("// reject: ".length())))) {
                    throw new GradleException(fixture.getName() + ": expected " + directive + ", got " + diagnostics);
                }
            }
            getLogger().lifecycle("Checked {} module boundary fixtures.", fixtures.size());
        }

        List<File> sources = getSources().getFiles().stream().sorted().toList();
        if (!sources.isEmpty()) {
            List<String> diagnostics = analyze(sources);
            if (!diagnostics.isEmpty()) {
                throw new GradleException(String.join(System.lineSeparator(), diagnostics));
            }
        }
    }

    private List<String> analyze(List<File> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || Runtime.version().feature() < 25) {
            throw new GradleException("Module API verification requires running Gradle with JDK 25 or newer.");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var errors = new ArrayList<String>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                List.of("--release", "25", "-proc:none", "-classpath", getClasspath().getAsPath()), null,
                files.getJavaFileObjectsFromFiles(sources));
            var units = task.parse();
            task.analyze();
            var trees = Trees.instance(task);
            var event = task.getElements().getTypeElement("org.bukkit.event.Event");
            for (CompilationUnitTree unit : units) {
                new Boundary(trees, task, event, unit, errors).scan(unit, null);
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic.toString());
            }
        }
        return errors;
    }

    private static final class Boundary extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final JavacTask task;
        private final TypeElement event;
        private final CompilationUnitTree unit;
        private final List<String> errors;
        private final Set<String> reported = new HashSet<>();

        Boundary(Trees trees, JavacTask task, TypeElement event, CompilationUnitTree unit, List<String> errors) {
            this.trees = trees;
            this.task = task;
            this.event = event;
            this.unit = unit;
            this.errors = errors;
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void ignored) {
            String pkg = node.getPackageName() == null ? "" : node.getPackageName().toString();
            if (pkg.equals(FRAMEWORK) || pkg.startsWith(FRAMEWORK + ".")) {
                report(node.getPackageName(), "declaring module code in a framework package");
            }
            return super.visitCompilationUnit(node, ignored);
        }

        @Override
        public Void visitImport(ImportTree node, Void ignored) {
            Tree selected = node.getQualifiedIdentifier();
            TreePath path = new TreePath(getCurrentPath(), selected);
            if (selected instanceof MemberSelectTree member && (node.isStatic() || member.getIdentifier().contentEquals("*"))) {
                path = new TreePath(path, member.getExpression());
            }
            if (trees.getElement(path) instanceof TypeElement type && (!isEvent(type.asType()) || node.isStatic())) {
                check(type.asType(), selected, false, new HashSet<>());
            }
            return null;
        }

        @Override
        public Void scan(Tree node, Void ignored) {
            if (node != null && getCurrentPath() != null) {
                TreePath path = new TreePath(getCurrentPath(), node);
                switch (node.getKind()) {
                    case IDENTIFIER, MEMBER_SELECT, METHOD_INVOCATION, MEMBER_REFERENCE, NEW_CLASS,
                        TYPE_CAST, PARAMETERIZED_TYPE, ARRAY_TYPE, VARIABLE, LAMBDA_EXPRESSION,
                        CONDITIONAL_EXPRESSION, ARRAY_ACCESS, NEW_ARRAY, ASSIGNMENT, INSTANCE_OF -> inspect(path);
                    default -> { }
                }
            }
            return super.scan(node, ignored);
        }

        private void inspect(TreePath path) {
            Tree leaf = path.getLeaf();
            Element element = trees.getElement(path);
            boolean eventToken = isEvent(trees.getTypeMirror(path)) && isEventToken(path);
            if (element != null && (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR
                    || element.getKind().isField()) && element.getEnclosingElement() instanceof TypeElement owner
                    && !owner.getQualifiedName().toString().isEmpty()) {
                String name = owner.getQualifiedName().toString();
                if (element.getKind() == ElementKind.CONSTRUCTOR && FRAMEWORK_CONSTRUCTORS.contains(name)) {
                    report(leaf, "constructing " + name);
                }
                // A class literal is a token. No raw event field or method is allowed.
                if (!(leaf instanceof MemberSelectTree select && select.getIdentifier().contentEquals("class"))) {
                    check(task.getTypes().erasure(owner.asType()), leaf, false, new HashSet<>());
                }
            }
            check(trees.getTypeMirror(path), leaf, eventToken, new HashSet<>());
        }

        private boolean isEventToken(TreePath path) {
            TreePath parent = path.getParentPath();
            if (parent == null) {
                return false;
            }
            if (parent.getLeaf() instanceof MemberSelectTree member && member.getIdentifier().contentEquals("class")) {
                return true;
            }
            if (parent.getLeaf() instanceof ParameterizedTypeTree generic && generic.getTypeArguments().contains(path.getLeaf())) {
                var raw = trees.getElement(new TreePath(parent, generic.getType()));
                return raw instanceof TypeElement type && type.getQualifiedName().contentEquals(GAME_EVENT);
            }
            return false;
        }

        private boolean isEvent(TypeMirror type) {
            return type != null && type.getKind() == TypeKind.DECLARED && event != null
                && task.getTypes().isSubtype(task.getTypes().erasure(type), event.asType());
        }

        private void check(TypeMirror type, Tree site, boolean eventToken, Set<TypeMirror> visited) {
            if (type == null || !visited.add(type)) {
                return;
            }
            switch (type) {
                case DeclaredType declared -> {
                    String name = ((TypeElement) declared.asElement()).getQualifiedName().toString();
                    if (isEvent(type)) {
                        if (!eventToken) {
                            report(site, name + " except as a GameEvent type token");
                        }
                    } else if (isForbidden(name)) {
                        report(site, name);
                    }
                    boolean tokenContainer = name.equals(GAME_EVENT) || name.equals("java.lang.Class");
                    for (TypeMirror arg : declared.getTypeArguments()) {
                        check(arg, site, tokenContainer, visited);
                    }
                }
                case ArrayType array -> check(array.getComponentType(), site, false, visited);
                case WildcardType wildcard -> {
                    check(wildcard.getExtendsBound(), site, eventToken, visited);
                    check(wildcard.getSuperBound(), site, false, visited);
                }
                case TypeVariable var -> check(var.getUpperBound(), site, eventToken, visited);
                case IntersectionType intersection -> intersection.getBounds().forEach(bound -> check(bound, site, eventToken, visited));
                case ExecutableType executable -> {
                    check(executable.getReturnType(), site, false, visited);
                    executable.getParameterTypes().forEach(argument -> check(argument, site, false, visited));
                }
                default -> { }
            }
        }

        private static boolean isForbidden(String name) {
            if (name.startsWith(FRAMEWORK + ".")) {
                String simple = name.substring(name.lastIndexOf('.') + 1);
                return !name.equals(GAME_MODULE) && (!name.startsWith(API) || simple.endsWith("Handle") || INTERNAL_API.contains(name));
            }
            if (name.startsWith("org.bukkit.") || name.startsWith("io.papermc.") || name.startsWith("com.destroystokyo.paper.")
                || name.startsWith("net.minecraft.") || name.startsWith("org.spigotmc.")) {
                return VALUES.stream().noneMatch(value -> name.equals(value) || name.startsWith(value + "."));
            }
            return false;
        }

        private void report(Tree site, String type) {
            long pos = trees.getSourcePositions().getStartPosition(unit, site);
            long line = pos < 0 ? 0 : unit.getLineMap().getLineNumber(pos);
            String error = Path.of(unit.getSourceFile().toUri()).getFileName() + ":" + line + ": " + PREFIX + type + ".";
            if (reported.add(error)) {
                errors.add(error);
            }
        }
    }
}
