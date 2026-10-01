package com.donutsforlife11.donutgame.api.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Read-only access to one file in a module's own datapack namespace.
 */
public final class GameDataResource {
    private final Path moduleJar;
    private final String namespace;
    private final String relativePath;
    private final String jarPath;

    GameDataResource(Path moduleJar, String namespace, String relativePath) {
        this.moduleJar = Objects.requireNonNull(moduleJar, "moduleJar").toAbsolutePath().normalize();
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.relativePath = normalizeRelativePath(relativePath);
        this.jarPath = "data/" + namespace + "/" + this.relativePath;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return relativePath;
    }

    public boolean exists() {
        try (JarFile jar = new JarFile(moduleJar.toFile())) {
            JarEntry entry = jar.getJarEntry(jarPath);
            return entry != null && !entry.isDirectory();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read module jar " + moduleJar + ".", exception);
        }
    }

    public byte[] bytes() {
        try (JarFile jar = new JarFile(moduleJar.toFile())) {
            JarEntry entry = jar.getJarEntry(jarPath);
            if (entry == null || entry.isDirectory()) {
                throw new IllegalArgumentException("Unknown module data resource: " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return input.readAllBytes();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read module data resource " + jarPath + ".", exception);
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
