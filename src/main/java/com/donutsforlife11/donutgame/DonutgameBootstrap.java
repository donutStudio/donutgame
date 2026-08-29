package com.donutsforlife11.donutgame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

public final class DonutgameBootstrap implements PluginBootstrap {
    private static final Pattern MODULES_DIRECTORY_PATTERN = Pattern.compile("^\\s*modules_directory\\s*:\\s*['\"]?([^'\"#]+)");
    private static final Pattern RUNTIME_DIRECTORY_PATTERN = Pattern.compile("^\\s*runtime_directory\\s*:\\s*['\"]?([^'\"#]+)");
    private static final String INTERNAL_PACK_METADATA = """
        {
          "pack": {
            "description": "Internal Donutgame module data",
            "min_format": [94, 1],
            "max_format": 94
          }
        }
        """;

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event ->
            discoverModuleDatapacks(context, event.registrar())
        );
    }

    private void discoverModuleDatapacks(BootstrapContext context, DatapackRegistrar registrar) {
        Path dataDirectory = context.getDataDirectory();
        Path modulesFolder = dataDirectory.resolve(readConfigValue(dataDirectory, MODULES_DIRECTORY_PATTERN, "modules")).normalize();
        Path datapacksFolder = dataDirectory.resolve(readConfigValue(dataDirectory, RUNTIME_DIRECTORY_PATTERN, ".runtime"))
            .resolve("datapacks")
            .normalize();
        if (!Files.isDirectory(modulesFolder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(modulesFolder)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .forEach(path -> discoverPack(context, registrar, datapacksFolder, path));
        } catch (IOException exception) {
            context.getLogger().warn("Failed to scan module datapacks in " + modulesFolder + ": " + exception.getMessage());
        }
    }

    private String readConfigValue(Path dataDirectory, Pattern pattern, String fallback) {
        Path config = dataDirectory.resolve("config.yml");
        if (!Files.isRegularFile(config)) {
            return fallback;
        }
        try (Stream<String> lines = Files.lines(config)) {
            return lines.map(pattern::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1).trim())
                .findFirst()
                .filter(value -> !value.isBlank())
                .orElse(fallback);
        } catch (IOException exception) {
            return fallback;
        }
    }

    private void discoverPack(BootstrapContext context, DatapackRegistrar registrar, Path datapacksFolder, Path moduleJar) {
        try {
            String id = "module-" + sanitizeId(moduleJar.getFileName().toString().replaceFirst("(?i)\\.jar$", ""));
            Path packFolder = datapacksFolder.resolve(id).normalize();
            if (!packFolder.startsWith(datapacksFolder)) {
                throw new IOException("Resolved datapack path escaped runtime folder: " + packFolder);
            }
            if (!extractDatapack(moduleJar, packFolder)) {
                return;
            }
            registrar.discoverPack(packFolder, id, configurer -> configurer
                .title(Component.text("Donutgame " + moduleJar.getFileName()))
                .autoEnableOnServerStart(true)
                .position(false, Datapack.Position.TOP)
            );
        } catch (IOException exception) {
            context.getLogger().warn("Failed to discover module datapack " + moduleJar + ": " + exception.getMessage());
        }
    }

    private boolean extractDatapack(Path moduleJar, Path packFolder) throws IOException {
        try (JarFile jar = new JarFile(moduleJar.toFile())) {
            boolean hasData = jar.stream().anyMatch(entry -> !entry.isDirectory() && entry.getName().startsWith("data/"));
            if (!hasData) {
                return false;
            }
            deleteDirectory(packFolder);
            Files.createDirectories(packFolder);
            Files.writeString(packFolder.resolve("pack.mcmeta"), INTERNAL_PACK_METADATA);
            for (JarEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("data/")) {
                    continue;
                }
                Path target = packFolder.resolve(name).normalize();
                if (!target.startsWith(packFolder)) {
                    throw new IOException("Refusing to extract unsafe module datapack entry: " + name);
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    try (OutputStream output = Files.newOutputStream(target)) {
                        input.transferTo(output);
                    }
                }
            }
            return true;
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String sanitizeId(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return sanitized.isBlank() ? "module" : sanitized;
    }
}
