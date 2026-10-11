import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GameObjectSpecsGenerateTask extends DefaultTask {
    private static final String OBJECT_PACKAGE_NAME = "com.donutsforlife11.donutgame.api.object";
    private static final String ITEM_PACKAGE_NAME = "com.donutsforlife11.donutgame.api.item";

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Classpath
    public abstract ConfigurableFileCollection getSourceArchives();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        try {
            deleteGeneratedFiles(outputDirectory);
            Files.createDirectories(outputDirectory.resolve(OBJECT_PACKAGE_NAME.replace('.', '/')));
            Files.createDirectories(outputDirectory.resolve(ITEM_PACKAGE_NAME.replace('.', '/')));

            URL[] urls = getClasspath().getFiles().stream()
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (IOException exception) {
                        throw new GradleException("Failed to build classpath URL for " + file, exception);
                    }
                })
                .toArray(URL[]::new);

            try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
                String materialSource = source("org/bukkit/Material.java");
                String entityTypeSource = source("org/bukkit/entity/EntityType.java");
                writeGameBlocks(outputDirectory, materialConstants(materialSource, "Blocks"));
                writeGameItems(outputDirectory, materialConstants(materialSource, "Items"));
                writeGameEntities(outputDirectory, entities(classLoader, entityTypeSource));
            }
        } catch (ReflectiveOperationException | IOException exception) {
            throw new GradleException("Failed to generate game object specs.", exception);
        }
    }

    private String source(String path) throws IOException {
        for (java.io.File file : getSourceArchives().getFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                JarEntry entry = jar.getJarEntry(path);
                if (entry == null) {
                    continue;
                }
                return new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new GradleException("Could not find " + path + " in source archives.");
    }

    private List<String> materialConstants(String source, String sectionName) {
        String section = section(source, sectionName);
        List<String> constants = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*([A-Z0-9_]+)\\(").matcher(section);
        while (matcher.find()) {
            constants.add(matcher.group(1));
        }
        constants.sort(Comparator.naturalOrder());
        return constants;
    }

    private List<String> entities(ClassLoader classLoader, String source) throws ReflectiveOperationException {
        Class<?> entityType = classLoader.loadClass("org.bukkit.entity.EntityType");
        List<String> entities = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*([A-Z0-9_]+)\\(\"[^\"]+\",\\s*([\\w.]+)\\.class,").matcher(section(source, "EntityType"));
        while (matcher.find()) {
            String constantName = matcher.group(1);
            if (deprecatedEnumConstant(entityType, constantName)) {
                continue;
            }
            entities.add(constantName);
        }
        entities.sort(Comparator.naturalOrder());
        return entities;
    }

    private Map<String, String> imports(String source) {
        Map<String, String> imports = new HashMap<>();
        Matcher matcher = Pattern.compile("(?m)^import\\s+([\\w.]+);").matcher(source);
        while (matcher.find()) {
            String fqcn = matcher.group(1);
            imports.put(fqcn.substring(fqcn.lastIndexOf('.') + 1), fqcn);
        }
        return imports;
    }

    private String section(String source, String sectionName) {
        String start = "// Start generate - " + sectionName;
        String end = "// End generate - " + sectionName;
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end);
        if (startIndex < 0 || endIndex < startIndex) {
            throw new GradleException("Could not find generated " + sectionName + " section.");
        }
        return source.substring(startIndex + start.length(), endIndex);
    }

    private boolean deprecatedEnumConstant(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            return field.isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }

    private void writeGameBlocks(Path outputDirectory, List<String> blocks) throws IOException {
        if (!blocks.contains("AIR")) {
            blocks = new ArrayList<>(blocks);
            blocks.add("AIR");
            blocks.sort(Comparator.naturalOrder());
        }
        StringBuilder source = generatedHeader(OBJECT_PACKAGE_NAME);
        source.append("public final class GameBlocks {\n");
        for (String block : blocks) {
            source.append("    public static final GameBlockType ")
                .append(block)
                .append(" = GameBlockType.of(org.bukkit.Material.")
                .append(block)
                .append(");\n");
        }
        source.append("\n");
        source.append("    private GameBlocks() {\n");
        source.append("    }\n\n");
        source.append("    public static GameBlockType of(org.bukkit.Material material) {\n");
        source.append("        return GameBlockType.of(material);\n");
        source.append("    }\n\n");
        source.append("    public static org.bukkit.block.data.BlockData data(org.bukkit.Material material) {\n");
        source.append("        return material.createBlockData();\n");
        source.append("    }\n");
        source.append("}\n");
        writeJava(outputDirectory, OBJECT_PACKAGE_NAME, "GameBlocks", source.toString());
    }

    private void writeGameItems(Path outputDirectory, List<String> items) throws IOException {
        StringBuilder source = generatedHeader(ITEM_PACKAGE_NAME);
        source.append("public final class GameItems {\n");
        for (String item : items) {
            source.append("    public static final GameItemType ")
                .append(item)
                .append(" = GameItemType.of(org.bukkit.Material.")
                .append(item)
                .append(");\n");
        }
        source.append("\n");
        source.append("    private GameItems() {\n");
        source.append("    }\n\n");
        source.append("    public static GameItemType of(org.bukkit.Material material) {\n");
        source.append("        return GameItemType.of(material);\n");
        source.append("    }\n\n");
        source.append("    public static GameItem item(org.bukkit.Material material) {\n");
        source.append("        return GameItem.of(material);\n");
        source.append("    }\n");
        source.append("\n");
        source.append("    public static GameItem item(String notation) {\n");
        source.append("        return ItemNotation.item(notation);\n");
        source.append("    }\n\n");
        source.append("    public static java.util.List<GameItem> items(String notation) {\n");
        source.append("        return ItemNotation.items(notation);\n");
        source.append("    }\n\n");
        source.append("    public static java.util.List<GameItem> items(java.util.Collection<String> notation) {\n");
        source.append("        return ItemNotation.items(notation);\n");
        source.append("    }\n\n");
        source.append("    public static org.bukkit.loot.LootTable lootTable(String notation) {\n");
        source.append("        return ItemNotation.lootTable(notation);\n");
        source.append("    }\n\n");
        source.append("    public static org.bukkit.loot.LootTable lootTable(java.util.Collection<?> items) {\n");
        source.append("        return ItemNotation.lootTable(items);\n");
        source.append("    }\n\n");
        source.append("    public static java.util.Collection<org.bukkit.inventory.ItemStack> fromLootTable(org.bukkit.loot.LootTable lootTable) {\n");
        source.append("        return ItemNotation.fromLootTable(lootTable);\n");
        source.append("    }\n\n");
        source.append("    public static java.util.Collection<org.bukkit.inventory.ItemStack> fromLootTable(org.bukkit.loot.LootTable lootTable, long seed) {\n");
        source.append("        return ItemNotation.fromLootTable(lootTable, seed);\n");
        source.append("    }\n\n");
        source.append("}\n");
        writeJava(outputDirectory, ITEM_PACKAGE_NAME, "GameItems", source.toString());
    }

    private void writeGameEntities(Path outputDirectory, List<String> entities) throws IOException {
        StringBuilder source = generatedHeader(OBJECT_PACKAGE_NAME);
        source.append("public final class GameEntities {\n");
        for (String entity : entities) {
            source.append("    public static final GameEntityType ")
                .append(entity)
                .append(" = GameEntityType.of(org.bukkit.entity.EntityType.")
                .append(entity)
                .append(");\n");
        }
        source.append("\n");
        source.append("    private GameEntities() {\n");
        source.append("    }\n\n");
        source.append("    public static GameEntityType of(org.bukkit.entity.EntityType type) {\n");
        source.append("        return GameEntityType.of(type);\n");
        source.append("    }\n");
        source.append("}\n");
        writeJava(outputDirectory, OBJECT_PACKAGE_NAME, "GameEntities", source.toString());
    }

    private StringBuilder generatedHeader(String packageName) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n");
        return source;
    }

    private void writeJava(Path outputDirectory, String packageName, String className, String source) throws IOException {
        Path file = outputDirectory.resolve(packageName.replace('.', '/')).resolve(className + ".java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private void deleteGeneratedFiles(Path outputDirectory) throws IOException {
        Path objectPackageDirectory = outputDirectory.resolve(OBJECT_PACKAGE_NAME.replace('.', '/'));
        Path itemPackageDirectory = outputDirectory.resolve(ITEM_PACKAGE_NAME.replace('.', '/'));
        Files.deleteIfExists(objectPackageDirectory.resolve("GameBlocks.java"));
        Files.deleteIfExists(objectPackageDirectory.resolve("GameItems.java"));
        Files.deleteIfExists(objectPackageDirectory.resolve("GameEntities.java"));
        Files.deleteIfExists(itemPackageDirectory.resolve("GameItems.java"));
    }

}
