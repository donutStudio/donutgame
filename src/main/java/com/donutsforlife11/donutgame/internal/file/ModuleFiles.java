package com.donutsforlife11.donutgame.internal.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Shared by early datapack discovery and the runtime module loader. */
public final class ModuleFiles {
    private ModuleFiles() {}

    public static Path directory(Path modules, String id) {
        if (id == null || !id.matches("[a-z0-9._-]+") || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        return resolve(modules, id);
    }

    public static Path resolve(Path directory, String relative) {
        Path root = directory.toAbsolutePath().normalize();
        if (relative == null || relative.isBlank() || relative.contains(":") || relative.contains("\\")) {
            throw new IllegalArgumentException("Expected a relative module file path: " + relative);
        }
        for (String part : relative.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid module file path: " + relative);
            }
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Module file escaped its directory: " + relative);
        for (Path path = target; path != null && path.startsWith(root); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Module file uses a symbolic link: " + path);
        }
        return target;
    }

    /** Jar resources are installation defaults; existing server files always win. */
    public static void seed(Path moduleJar, Path directory) throws IOException {
        Files.createDirectories(directory);
        try (JarFile jar = new JarFile(moduleJar.toFile())) {
            for (var entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                String name = entry.getName();
                if (name.startsWith("META-INF/") || name.endsWith(".class")) continue;
                Path target = resolve(directory, name);
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, target);
                }
            }
        }
    }
}
