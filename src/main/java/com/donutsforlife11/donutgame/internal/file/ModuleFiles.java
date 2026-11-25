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

    /** Classloaders must never read the deployment jar after it can be overwritten by an upload. */
    public static Path snapshot(Path moduleJar, Path snapshots) throws IOException {
        Files.createDirectories(snapshots);
        Path copy = Files.createTempFile(snapshots, "module-", ".jar");
        try {
            Files.copy(moduleJar, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // RODO: Check every local header and CRC before retaining a snapshot for lazy class loading.
            int entries = 0;
            try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(copy))) {
                while (zip.getNextEntry() != null) {
                    zip.transferTo(java.io.OutputStream.nullOutputStream());
                    zip.closeEntry();
                    entries++;
                }
            }
            if (entries == 0) throw new IOException("Module is empty or is not a jar: " + moduleJar);
            try (var jar = new JarFile(copy.toFile())) {
                if (jar.getJarEntry("config.yml") == null) throw new IOException("Module jar is missing config.yml");
            }
            return copy;
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(copy);
            throw error;
        }
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
