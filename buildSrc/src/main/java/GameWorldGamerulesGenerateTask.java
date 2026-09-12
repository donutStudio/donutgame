import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GameWorldGamerulesGenerateTask extends DefaultTask {
    private static final String PACKAGE_NAME = "com.donutsforlife11.donutgame.api.map";

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        try {
            deleteGeneratedFile(outputDirectory);
            Files.createDirectories(outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')));

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
                Class<?> gameRuleType = classLoader.loadClass("org.bukkit.GameRule");
                Class<?> gameRulesType = classLoader.loadClass("org.bukkit.GameRules");
                List<GameRuleData> gamerules = new ArrayList<>();
                for (Field field : gameRulesType.getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())
                        || !gameRuleType.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    TypeData javaType = javaType(field.getGenericType());
                    if (javaType != null) {
                        gamerules.add(new GameRuleData(field.getName(), methodName(field.getName()), javaType));
                    }
                }
                gamerules.sort(Comparator.comparing(GameRuleData::methodName));
                writeGamerules(outputDirectory, gamerules);
            }
        } catch (ReflectiveOperationException | IOException exception) {
            throw new GradleException("Failed to generate GameWorld gamerules API.", exception);
        }
    }

    private TypeData javaType(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType) || parameterizedType.getActualTypeArguments().length != 1) {
            return null;
        }
        Type argument = parameterizedType.getActualTypeArguments()[0];
        if (argument == Boolean.class) {
            return new TypeData("boolean", "Boolean", "false");
        }
        if (argument == Integer.class) {
            return new TypeData("int", "Integer", "0");
        }
        return null;
    }

    private String methodName(String constantName) {
        StringBuilder method = new StringBuilder();
        boolean uppercaseNext = false;
        for (char character : constantName.toLowerCase(Locale.ROOT).toCharArray()) {
            if (!Character.isLetterOrDigit(character)) {
                uppercaseNext = true;
                continue;
            }
            if (method.isEmpty()) {
                method.append(Character.toLowerCase(character));
                uppercaseNext = false;
                continue;
            }
            method.append(uppercaseNext ? Character.toUpperCase(character) : character);
            uppercaseNext = false;
        }
        return method.toString();
    }

    private void writeGamerules(Path outputDirectory, List<GameRuleData> gamerules) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(PACKAGE_NAME).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n");
        source.append("public class GameWorldGamerules {\n");
        source.append("    private final GameWorld world;\n\n");
        source.append("    public GameWorldGamerules(GameWorld world) {\n");
        source.append("        this.world = java.util.Objects.requireNonNull(world, \"world\");\n");
        source.append("    }\n");
        for (GameRuleData gamerule : gamerules) {
            source.append("\n");
            source.append("    public ").append(gamerule.type().primitiveType()).append(" ").append(gamerule.methodName()).append("() {\n");
            source.append("        ").append(gamerule.type().boxedType()).append(" value = world.bukkitWorld().getGameRuleValue(org.bukkit.GameRules.").append(gamerule.constantName()).append(");\n");
            source.append("        return value == null ? ").append(gamerule.type().fallback()).append(" : value;\n");
            source.append("    }\n\n");
            source.append("    public void ").append(gamerule.methodName()).append("(").append(gamerule.type().primitiveType()).append(" value) {\n");
            source.append("        world.bukkitWorld().setGameRule(org.bukkit.GameRules.").append(gamerule.constantName()).append(", value);\n");
            source.append("    }\n");
        }
        source.append("}\n");
        Path file = outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')).resolve("GameWorldGamerules.java");
        Files.writeString(file, source.toString(), StandardCharsets.UTF_8);
    }

    private void deleteGeneratedFile(Path outputDirectory) throws IOException {
        Path file = outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')).resolve("GameWorldGamerules.java");
        Files.deleteIfExists(file);
    }

    private record GameRuleData(String constantName, String methodName, TypeData type) {
    }

    private record TypeData(String primitiveType, String boxedType, String fallback) {
    }
}
