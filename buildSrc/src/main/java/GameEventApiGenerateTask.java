import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GameEventApiGenerateTask extends DefaultTask {
    private static final String PACKAGE_NAME = "com.donutsforlife11.donutgame.api.event";
    private static final Set<String> PLUMBING_METHODS = Set.of(
        "getHandlers",
        "getHandlerList",
        "getEventName",
        "isAsynchronous",
        "callEvent"
    );
    private static final Set<String> UNSAFE_RETURNS = Set.of(
        "org.bukkit.event.HandlerList",
        "org.bukkit.Server",
        "org.bukkit.plugin.Plugin",
        "org.bukkit.command.Command",
        "java.lang.Class"
    );
    private static final Set<String> AMBIGUOUS_SHARED_METHODS = Set.of(
        "getAction",
        "getBedEnterResult",
        "getBlocks",
        "getCause",
        "getCombuster",
        "getConnection",
        "getInput",
        "getInventory",
        "getItem",
        "getRadius",
        "getReason",
        "getRecipe",
        "getResult",
        "getSlot",
        "getSource",
        "getType",
        "getView"
    );

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputFile
    public abstract RegularFileProperty getAdapterRegistrySource();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        try {
            deleteGeneratedPackage(outputDirectory);
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
                Class<?> eventType = classLoader.loadClass("org.bukkit.event.Event");
                List<TypeMapping> mappings = typeMappings(classLoader);
                List<Class<?>> eventClasses = eventClasses(classLoader, eventType);
                List<GeneratedMethod> methods = generatedMethods(eventClasses, mappings);
                writeInterfaces(outputDirectory, methods);
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new GradleException("Failed to generate GameEvent API.", exception);
        }
    }

    private List<TypeMapping> typeMappings(ClassLoader classLoader) throws IOException {
        String source = Files.readString(getAdapterRegistrySource().get().getAsFile().toPath(), StandardCharsets.UTF_8);
        Map<String, String> imports = imports(source);
        Map<String, String> configuredMappings = new LinkedHashMap<>();
        collectMappings(
            configuredMappings,
            Pattern.compile("new\\s+TypeMapping\\(\\s*([\\w.]+)\\.class\\.getName\\(\\)\\s*,\\s*([\\w.]+)\\.class\\.getName\\(\\)\\s*\\)").matcher(source),
            imports
        );
        collectMappings(
            configuredMappings,
            Pattern.compile("\\.register\\(\\s*([\\w.]+)\\.class\\s*,\\s*([\\w.]+)\\.class\\s*,").matcher(source),
            imports
        );

        List<TypeMapping> mappings = new ArrayList<>();
        for (Map.Entry<String, String> entry : configuredMappings.entrySet()) {
            try {
                mappings.add(new TypeMapping(classLoader.loadClass(entry.getKey()), entry.getValue()));
            } catch (ClassNotFoundException exception) {
                throw new GradleException("GameEvent mapping source type is not on the generator classpath: " + entry.getKey(), exception);
            }
        }
        mappings.sort(Comparator.comparingInt((TypeMapping mapping) -> inheritanceDepth(mapping.sourceType())).reversed());
        return mappings;
    }

    private void collectMappings(Map<String, String> mappings, Matcher matcher, Map<String, String> imports) {
        while (matcher.find()) {
            mappings.put(resolveType(matcher.group(1), imports), resolveType(matcher.group(2), imports));
        }
    }

    private Map<String, String> imports(String source) {
        Map<String, String> imports = new HashMap<>();
        Matcher matcher = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE).matcher(source);
        while (matcher.find()) {
            String fqcn = matcher.group(1);
            imports.put(fqcn.substring(fqcn.lastIndexOf('.') + 1), fqcn);
        }
        return imports;
    }

    private String resolveType(String name, Map<String, String> imports) {
        if (name.contains(".")) {
            return name;
        }
        String imported = imports.get(name);
        return imported == null ? "java.lang." + name : imported;
    }

    private List<Class<?>> eventClasses(ClassLoader classLoader, Class<?> eventType) throws IOException {
        Set<String> classNames = new TreeSet<>();
        for (java.io.File file : getClasspath().getFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("org/bukkit/event/"))
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .forEach(classNames::add);
            }
        }

        List<Class<?>> eventClasses = new ArrayList<>();
        for (String className : classNames) {
            try {
                Class<?> candidate = Class.forName(className, false, classLoader);
                int modifiers = candidate.getModifiers();
                if (!candidate.equals(eventType)
                    && eventType.isAssignableFrom(candidate)
                    && !candidate.isInterface()
                    && !Modifier.isAbstract(modifiers)) {
                    eventClasses.add(candidate);
                }
            } catch (LinkageError | ClassNotFoundException ignored) {
            }
        }
        return eventClasses;
    }

    private List<GeneratedMethod> generatedMethods(List<Class<?>> eventClasses, List<TypeMapping> mappings) {
        Map<Signature, GeneratedMethod> methods = new LinkedHashMap<>();
        Map<Signature, Set<String>> conflicts = new LinkedHashMap<>();
        for (Class<?> eventClass : eventClasses) {
            for (Method method : eventClass.getMethods()) {
                GeneratedMethod generated = generatedMethod(method, mappings);
                if (generated == null) {
                    continue;
                }
                GeneratedMethod existing = methods.get(generated.signature());
                if (existing == null) {
                    methods.put(generated.signature(), generated);
                    continue;
                }
                if (!existing.returnType().equals(generated.returnType()) && existing.returnType().numeric() && generated.returnType().numeric()) {
                    methods.put(generated.signature(), existing.withReturnType(TypeRef.primitive("double", "0D")));
                    continue;
                }
                if (!existing.returnType().equals(generated.returnType())) {
                    conflicts.computeIfAbsent(generated.signature(), ignored -> new LinkedHashSet<>())
                        .add(existing.returnType().javaType() + " from " + existing.nativeDeclaration());
                    conflicts.get(generated.signature()).add(generated.returnType().javaType() + " from " + generated.nativeDeclaration());
                }
            }
        }
        if (!conflicts.isEmpty()) {
            StringBuilder report = new StringBuilder("GameEvent API generation found mapped method-signature conflicts:");
            conflicts.forEach((signature, declarations) -> {
                report.append(System.lineSeparator()).append("  ").append(signature.render()).append(":");
                declarations.forEach(declaration -> report.append(System.lineSeparator()).append("    ").append(declaration));
            });
            throw new GradleException(report.toString());
        }
        return methods.values().stream()
            .sorted(Comparator.comparing(GeneratedMethod::category).thenComparing(method -> method.signature().render()))
            .toList();
    }

    private GeneratedMethod generatedMethod(Method method, List<TypeMapping> mappings) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        if (method.getDeclaringClass() == Object.class
            || PLUMBING_METHODS.contains(method.getName())
            || AMBIGUOUS_SHARED_METHODS.contains(method.getName())) {
            return null;
        }
        if (deprecated(method.getDeclaringClass())
            || method.isAnnotationPresent(Deprecated.class)
            || deprecated(method.getReturnType())
            || Stream.of(method.getParameterTypes()).anyMatch(this::deprecated)) {
            return null;
        }
        if (UNSAFE_RETURNS.contains(method.getReturnType().getName())) {
            return null;
        }

        AccessKind accessKind = accessKind(method);
        if (accessKind == null) {
            return null;
        }

        String property = accessKind.property(method.getName());
        TypeRef returnType = javaType(property, method.getGenericReturnType(), method.getReturnType(), mappings);
        if (returnType == null || (returnType.primitiveVoid() && accessKind != AccessKind.SETTER)) {
            return null;
        }

        List<TypeRef> parameters = new ArrayList<>();
        for (int i = 0; i < method.getParameterCount(); i++) {
            TypeRef parameter = javaType(property, method.getGenericParameterTypes()[i], method.getParameterTypes()[i], mappings);
            if (parameter == null || parameter.primitiveVoid()) {
                return null;
            }
            parameters.add(parameter);
        }

        Category category = category(method, returnType, parameters);
        Signature signature = new Signature(method.getName(), parameters.stream().map(TypeRef::javaType).toList());
        String nativeDeclaration = method.getDeclaringClass().getName() + "#" + method.getName();
        return new GeneratedMethod(signature, method.getName(), property, returnType, parameters, accessKind, category, nativeDeclaration);
    }

    private AccessKind accessKind(Method method) {
        String name = method.getName();
        if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
            if (name.startsWith("get") && name.length() > 3) {
                return AccessKind.GETTER;
            }
            if (name.startsWith("is") && name.length() > 2 && method.getReturnType() == Boolean.TYPE) {
                return AccessKind.BOOLEAN_GETTER;
            }
            if (Character.isLowerCase(name.charAt(0))) {
                return AccessKind.DIRECT_GETTER;
            }
        }
        if (method.getParameterCount() == 1 && method.getReturnType() == Void.TYPE && name.startsWith("set") && name.length() > 3) {
            return AccessKind.SETTER;
        }
        return null;
    }

    private TypeRef javaType(String property, Type genericType, Class<?> rawType, List<TypeMapping> mappings) {
        if (rawType == Void.TYPE) {
            return TypeRef.primitive("void", "null");
        }
        if (!isPropertyTypeAllowed(property, rawType)) {
            return null;
        }
        if (rawType.isPrimitive()) {
            return TypeRef.primitive(rawType.getName(), primitiveFallback(rawType));
        }
        TypeMapping mapping = mappedType(property, rawType, mappings);
        if (mapping != null) {
            return TypeRef.mapped(mapping.targetType());
        }
        if (genericType instanceof ParameterizedType parameterized
            && rawType == List.class
            && parameterized.getActualTypeArguments().length == 1
            && parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            TypeRef element = javaType(property, elementType, elementType, mappings);
            if (element == null || element.primitiveVoid()) {
                return null;
            }
            return TypeRef.list(element.javaType());
        }
        if (isSafe(rawType)) {
            return TypeRef.safe(javaTypeName(genericType), javaName(rawType));
        }
        return null;
    }

    private TypeMapping mappedType(String property, Class<?> type, List<TypeMapping> mappings) {
        if (isEntityLikeProperty(property) && entityType(mappings).sourceType().isAssignableFrom(type)) {
            return entityType(mappings);
        }
        for (TypeMapping mapping : mappings) {
            if (mapping.sourceType().isAssignableFrom(type)) {
                return mapping;
            }
        }
        return null;
    }

    private boolean isPropertyTypeAllowed(String property, Class<?> rawType) {
        return switch (property) {
            case "player" -> rawType.getName().equals("org.bukkit.entity.Player");
            case "from", "to" -> rawType.getName().equals("org.bukkit.Location");
            case "damager", "combuster" -> isBukkitEntity(rawType);
            default -> true;
        };
    }

    private boolean isEntityLikeProperty(String property) {
        return "entity".equals(property)
            || "damager".equals(property)
            || "combuster".equals(property)
            || "projectile".equals(property)
            || "shooter".equals(property)
            || "vehicle".equals(property);
    }

    private boolean isBukkitEntity(Class<?> type) {
        return type.getName().equals("org.bukkit.entity.Entity")
            || type.getInterfaces().length > 0 && Stream.of(type.getInterfaces()).anyMatch(this::isBukkitEntity)
            || type.getSuperclass() != null && isBukkitEntity(type.getSuperclass());
    }

    private TypeMapping entityType(List<TypeMapping> mappings) {
        return mappings.stream()
            .filter(mapping -> mapping.sourceType().getName().equals("org.bukkit.entity.Entity"))
            .findFirst()
            .orElseThrow(() -> new GradleException("GameEvent mappings must include org.bukkit.entity.Entity."));
    }

    private boolean isSafe(Class<?> type) {
        if (type.isEnum()
            || type == String.class
            || Number.class.isAssignableFrom(type)
            || type == Boolean.class
            || type == Character.class) {
            return true;
        }
        Package pkg = type.getPackage();
        String packageName = pkg == null ? "" : pkg.getName();
        return packageName.startsWith("org.bukkit")
            || packageName.startsWith("io.papermc.paper")
            || packageName.startsWith("net.kyori.adventure");
    }

    private boolean deprecated(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            if (current.isAnnotationPresent(Deprecated.class)) {
                return true;
            }
        }
        return false;
    }

    private String javaName(Class<?> type) {
        String canonicalName = type.getCanonicalName();
        return canonicalName == null ? type.getName().replace('$', '.') : canonicalName;
    }

    private String javaTypeName(Type type) {
        if (type instanceof Class<?> typeClass) {
            return javaName(typeClass);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (!(rawType instanceof Class<?> rawClass)) {
                return null;
            }
            List<String> arguments = new ArrayList<>();
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                arguments.add(javaTypeArgumentName(argument));
            }
            return javaName(rawClass) + "<" + String.join(", ", arguments) + ">";
        }
        if (type instanceof TypeVariable<?>) {
            return "?";
        }
        if (type instanceof WildcardType) {
            return "?";
        }
        return null;
    }

    private String javaTypeArgumentName(Type type) {
        String name = javaTypeName(type);
        return name == null ? "?" : name;
    }

    private Category category(Method method, TypeRef returnType, List<TypeRef> parameters) {
        String text = (method.getName() + " " + returnType.javaType() + " " + parameters).toLowerCase(Locale.ROOT);
        if (text.contains("damage") || text.contains("death") || text.contains("health") || text.contains("exp")) {
            return Category.DAMAGE;
        }
        if (text.contains("player") || text.contains("entity") || text.contains("damager") || text.contains("projectile")
            || text.contains("shooter") || text.contains("vehicle")) {
            return Category.ACTOR;
        }
        if (text.contains("location") || text.contains("world") || text.contains("block") || text.contains("from") || text.contains("to")) {
            return Category.LOCATION;
        }
        if (text.contains("cancel") || text.contains("result") || text.contains("message") || text.contains("keep")
            || text.contains("drop") || text.contains("use") || text.contains("state")) {
            return Category.STATE;
        }
        return Category.GENERAL;
    }

    private void writeInterfaces(Path outputDirectory, List<GeneratedMethod> methods) throws IOException {
        Map<Category, List<GeneratedMethod>> byCategory = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (GeneratedMethod method : methods) {
            byCategory.get(method.category()).add(method);
        }

        for (Map.Entry<Category, List<GeneratedMethod>> entry : byCategory.entrySet()) {
            writeInterface(outputDirectory, entry.getKey().interfaceName(), entry.getValue());
        }
        writeAggregate(outputDirectory);
    }

    private void writeInterface(Path outputDirectory, String interfaceName, List<GeneratedMethod> methods) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(PACKAGE_NAME).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n");
        source.append("public interface ").append(interfaceName).append("<E extends org.bukkit.event.Event> extends GameEventAccess<E> {\n");
        for (GeneratedMethod method : methods) {
            source.append("\n");
            source.append(methodSource(method));
        }
        source.append("}\n");
        writeJava(outputDirectory, interfaceName, source.toString());
    }

    private void writeAggregate(Path outputDirectory) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(PACKAGE_NAME).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n");
        source.append("public interface GameEventProperties<E extends org.bukkit.event.Event> extends\n");
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            source.append("    ").append(category.interfaceName()).append("<E>");
            source.append(i == Category.values().length - 1 ? " {\n" : ",\n");
        }
        source.append("}\n");
        writeJava(outputDirectory, "GameEventProperties", source.toString());
    }

    private String methodSource(GeneratedMethod method) {
        StringBuilder source = new StringBuilder();
        if (method.returnType().list()) {
            source.append("    @SuppressWarnings(\"unchecked\")\n");
        }
        source.append("    default ").append(method.returnType().javaType()).append(" ").append(method.name()).append("(");
        for (int i = 0; i < method.parameters().size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            source.append(method.parameters().get(i).javaType()).append(" arg").append(i);
        }
        source.append(") {\n");
        if (method.accessKind() == AccessKind.SETTER) {
            source.append("        set(\"").append(method.property()).append("\", arg0);\n");
            source.append("    }\n");
            return source.toString();
        }
        TypeRef returnType = method.returnType();
        if (returnType.mapped() || returnType.safe()) {
            if (returnType.parameterized()) {
                source.append("        return (").append(returnType.javaType()).append(") getRaw(\"").append(method.property()).append("\");\n");
            } else {
                source.append("        return get(\"").append(method.property()).append("\", ").append(returnType.rawClass()).append(".class);\n");
            }
        } else if (returnType.list()) {
            source.append("        return (").append(returnType.javaType()).append(") getRaw(\"").append(method.property()).append("\");\n");
        } else {
            source.append("        Object value = getRaw(\"").append(method.property()).append("\");\n");
            source.append("        return value instanceof ").append(returnType.boxedType()).append(" typed ? typed").append(returnType.primitiveAccessor()).append(" : ").append(returnType.fallback()).append(";\n");
        }
        source.append("    }\n");
        return source.toString();
    }

    private void writeJava(Path outputDirectory, String className, String source) throws IOException {
        Path file = outputDirectory.resolve(PACKAGE_NAME.replace('.', '/')).resolve(className + ".java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private void deleteGeneratedPackage(Path outputDirectory) throws IOException {
        Path packageDirectory = outputDirectory.resolve(PACKAGE_NAME.replace('.', '/'));
        if (!Files.exists(packageDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(packageDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private int inheritanceDepth(Class<?> type) {
        int depth = 0;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            depth++;
        }
        return depth;
    }

    private String primitiveFallback(Class<?> type) {
        if (type == Boolean.TYPE) return "false";
        if (type == Character.TYPE) return "'\\0'";
        if (type == Float.TYPE) return "0F";
        if (type == Long.TYPE) return "0L";
        if (type == Double.TYPE) return "0D";
        return "0";
    }

    private enum AccessKind {
        GETTER {
            @Override
            String property(String methodName) {
                return decapitalize(methodName.substring(3));
            }
        },
        BOOLEAN_GETTER {
            @Override
            String property(String methodName) {
                return decapitalize(methodName.substring(2));
            }
        },
        DIRECT_GETTER {
            @Override
            String property(String methodName) {
                return methodName;
            }
        },
        SETTER {
            @Override
            String property(String methodName) {
                return decapitalize(methodName.substring(3));
            }
        };

        abstract String property(String methodName);

        static String decapitalize(String value) {
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
    }

    private enum Category {
        ACTOR("GameEventActorMethods"),
        LOCATION("GameEventLocationMethods"),
        DAMAGE("GameEventDamageMethods"),
        STATE("GameEventStateMethods"),
        GENERAL("GameEventGeneralMethods");

        private final String interfaceName;

        Category(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        String interfaceName() {
            return interfaceName;
        }
    }

    private record TypeMapping(Class<?> sourceType, String targetType) {
    }

    private record Signature(String name, List<String> parameters) {
        String render() {
            return name + "(" + String.join(", ", parameters) + ")";
        }
    }

    private record GeneratedMethod(
        Signature signature,
        String name,
        String property,
        TypeRef returnType,
        List<TypeRef> parameters,
        AccessKind accessKind,
        Category category,
        String nativeDeclaration
    ) {
        GeneratedMethod withReturnType(TypeRef returnType) {
            return new GeneratedMethod(signature, name, property, returnType, parameters, accessKind, category, nativeDeclaration);
        }
    }

    private record TypeRef(
        String javaType,
        String rawClass,
        boolean mapped,
        boolean safe,
        boolean list,
        boolean primitiveVoid,
        boolean numeric,
        boolean parameterized,
        String boxedType,
        String primitiveAccessor,
        String fallback
    ) {
        static TypeRef mapped(String javaType) {
            return new TypeRef(javaType, javaType, true, false, false, false, false, false, null, null, null);
        }

        static TypeRef safe(String javaType, String rawClass) {
            return new TypeRef(javaType, rawClass, false, true, false, false, false, javaType.contains("<"), null, null, null);
        }

        static TypeRef list(String javaType) {
            return new TypeRef("java.util.List<" + javaType + ">", "java.util.List", false, false, true, false, false, true, null, null, null);
        }

        static TypeRef primitive(String javaType, String fallback) {
            if ("void".equals(javaType)) {
                return new TypeRef(javaType, null, false, false, false, true, false, false, null, null, null);
            }
            return new TypeRef(javaType, null, false, false, false, false, isNumeric(javaType), false, boxedType(javaType), primitiveAccessor(javaType), fallback);
        }

        private static boolean isNumeric(String javaType) {
            return !"boolean".equals(javaType) && !"char".equals(javaType);
        }

        private static String boxedType(String javaType) {
            return switch (javaType) {
                case "boolean" -> "Boolean";
                case "byte" -> "Byte";
                case "short" -> "Short";
                case "int" -> "Integer";
                case "long" -> "Long";
                case "float" -> "Float";
                case "double" -> "Double";
                case "char" -> "Character";
                default -> throw new IllegalArgumentException("Unsupported primitive " + javaType);
            };
        }

        private static String primitiveAccessor(String javaType) {
            return switch (javaType) {
                case "boolean" -> ".booleanValue()";
                case "byte" -> ".byteValue()";
                case "short" -> ".shortValue()";
                case "int" -> ".intValue()";
                case "long" -> ".longValue()";
                case "float" -> ".floatValue()";
                case "double" -> ".doubleValue()";
                case "char" -> ".charValue()";
                default -> throw new IllegalArgumentException("Unsupported primitive " + javaType);
            };
        }
    }
}
