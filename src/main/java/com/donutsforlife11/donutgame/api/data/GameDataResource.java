package com.donutsforlife11.donutgame.api.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Objects;
import com.donutsforlife11.donutgame.internal.file.ModuleFiles;

/**
 * Read-only access to one file in a module's own datapack namespace.
 */
public final class GameDataResource {
    private final Path moduleDirectory;
    private final String namespace;
    private final String relativePath;
    private final String filePath;

    GameDataResource(Path moduleDirectory, String namespace, String relativePath) {
        this.moduleDirectory = Objects.requireNonNull(moduleDirectory, "moduleDirectory").toAbsolutePath().normalize();
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.relativePath = normalizeRelativePath(relativePath);
        this.filePath = "data/" + namespace + "/" + this.relativePath;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return relativePath;
    }

    public boolean exists() {
        return Files.isRegularFile(ModuleFiles.resolve(moduleDirectory, filePath));
    }

    public byte[] bytes() {
        try {
            return Files.readAllBytes(ModuleFiles.resolve(moduleDirectory, filePath));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read module data resource " + filePath + ".", exception);
        }
    }

    public InputStream open() {
        return new ByteArrayInputStream(bytes());
    }

    public String text() {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return namespace + ":" + relativePath;
    }

    private static String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Data resource path cannot be blank.");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Data resource path must be relative: " + path);
        }
        for (String part : normalized.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid data resource path: " + path);
            }
        }
        return normalized;
    }
}
