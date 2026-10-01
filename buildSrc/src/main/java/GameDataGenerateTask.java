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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates GameData getters directly from Paper's RegistryKey API.
 *
 * Paper documents each RegistryKey as built-in or data-driven. We intentionally
 * read that metadata from the pinned Paper sources instead of maintaining a
 * second list in Donutgame.
 */
public abstract class GameDataGenerateTask extends DefaultTask {
    private static final String PACKAGE_NAME = "com.donutsforlife11.donutgame.api.data";
    private static final String REGISTRY_KEY_SOURCE = "io/papermc/paper/registry/RegistryKey.java";

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
                String registryKeySource = source(REGISTRY_KEY_SOURCE);
                Class<?> registryKeyType = classLoader.loadClass("io.papermc.paper.registry.RegistryKey");
                Class<?> keyedType = classLoader.loadClass("org.bukkit.Keyed");

                List<RegistryData> registries = registries(registryKeyType, keyedType, registryKeySource);
                if (registries.stream().noneMatch(RegistryData::dataDriven)) {
                    throw new GradleException("Paper RegistryKey source did not expose any data-driven registries.");
                }
                writeGameData(outputDirectory, registries);
            }
        } catch (ReflectiveOperationException | IOException exception) {
            throw new GradleException("Failed to generate GameData API.", exception);
        }
    }

    private List<RegistryData> registries(Class<?> registryKeyType, Class<?> keyedType, String source) {
        List<RegistryData> registries = new ArrayList<>();
        for (Field field : registryKeyType.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != registryKeyType) {
                continue;
            }
            if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)
                || parameterizedType.getActualTypeArguments().length != 1) {
                continue;
            }

            Type valueType = parameterizedType.getActualTypeArguments()[0];
            if (!isKeyed(valueType, keyedType)) {
                continue;
            }

            String methodName = methodName(field.getName());
            registries.add(new RegistryData(
                field.getName(),
                methodName,
                javaType(valueType),
                isDataDriven(source, field.getName())
            ));
        }
        registries.sort(Comparator.comparing(RegistryData::methodName));
        ensureUniqueMethodNames(registries);
        return registries;
    }

    private boolean isKeyed(Type type, Class<?> keyedType) {
        if (type instanceof Class<?> valueClass) {
            return keyedType.isAssignableFrom(valueClass);
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawClass) {
            return keyedType.isAssignableFrom(rawClass);
        }
        return false;
    }

    private boolean isDataDriven(String source, String fieldName) {
        Matcher fieldMatcher = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\s*=").matcher(source);
        if (!fieldMatcher.find()) {
            throw new GradleException("Could not find RegistryKey." + fieldName + " in Paper sources.");
        }
        int fieldStart = fieldMatcher.start();
        int commentStart = source.lastIndexOf("/**", fieldStart);
        int commentEnd = source.lastIndexOf("*/", fieldStart);
        if (commentStart < 0 || commentEnd < commentStart) {
            return false;
        }
        return source.substring(commentStart, commentEnd).contains("Data-driven registry");
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

    private void writeGameData(Path outputDirectory, List<RegistryData> registries) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(PACKAGE_NAME).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n");
        source.append("public final class GameData extends GameDataBase {\n");
        source.append("    public GameData(com.donutsforlife11.donutgame.internal.game.GameModule module, java.io.File moduleJar) {\n");
        source.append("        super(module, moduleJar);\n");
        source.append("    }\n");

        for (RegistryData registry : registries) {
            if (registry.dataDriven()) {
                source.append("\n");
                source.append("    public ").append(registry.javaType()).append(" ").append(registry.methodName())
                    .append("(org.bukkit.NamespacedKey key) {\n");
                source.append("        return registry(io.papermc.paper.registry.RegistryKey.")
                    .append(registry.constantName()).append(", key);\n");
                source.append("    }\n\n");
                source.append("    public ").append(registry.javaType()).append(" ").append(registry.methodName())
                    .append("(String key) {\n");
                source.append("        return ").append(registry.methodName()).append("(ownedKey(key));\n");
                source.append("    }\n");
            }

            source.append("\n");
            source.append("    public org.bukkit.Tag<").append(registry.javaType()).append("> ")
                .append(registry.methodName()).append("Tag(org.bukkit.NamespacedKey key) {\n");
            source.append("        return tag(io.papermc.paper.registry.RegistryKey.")
                .append(registry.constantName()).append(", key);\n");
            source.append("    }\n\n");
            source.append("    public org.bukkit.Tag<").append(registry.javaType()).append("> ")
                .append(registry.methodName()).append("Tag(String key) {\n");
            source.append("        return ").append(registry.methodName()).append("Tag(ownedKey(key));\n");
            source.append("    }\n");
        }

        source.append("}\n");
        Path file = outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')).resolve("GameData.java");
        Files.writeString(file, source.toString(), StandardCharsets.UTF_8);
    }

    private void ensureUniqueMethodNames(List<RegistryData> registries) {
        Set<String> names = new HashSet<>();
        for (RegistryData registry : registries) {
            if (!names.add(registry.methodName())) {
                throw new GradleException("Multiple Paper RegistryKey constants map to GameData method " + registry.methodName() + ".");
            }
        }
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
                method.append(character);
                uppercaseNext = false;
                continue;
            }
            method.append(uppercaseNext ? Character.toUpperCase(character) : character);
            uppercaseNext = false;
        }
        return method.toString();
    }

    private String javaType(Type type) {
        return type.getTypeName().replace('$', '.');
    }

    private void deleteGeneratedFile(Path outputDirectory) throws IOException {
        Files.deleteIfExists(outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')).resolve("GameData.java"));
    }

    private record RegistryData(String constantName, String methodName, String javaType, boolean dataDriven) {
    }
}
