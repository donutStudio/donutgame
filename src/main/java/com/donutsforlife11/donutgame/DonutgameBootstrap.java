package com.donutsforlife11.donutgame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.donutsforlife11.donutgame.internal.file.ModuleFiles;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

/**
 * Exposes each module folder's data/<module-id>/ directory to Minecraft as a
 * private-to-the-module source pack. Minecraft itself still merges enabled
 * datapacks into global registries; GameData provides the module ownership
 * boundary used by module code.
 */
public final class DonutgameBootstrap implements PluginBootstrap {
    private static final Pattern MODULES_DIRECTORY_PATTERN = Pattern.compile("^\\s*modules_directory\\s*:\\s*['\"]?([^'\"#]+)");
    private static final Pattern RUNTIME_DIRECTORY_PATTERN = Pattern.compile("^\\s*runtime_directory\\s*:\\s*['\"]?([^'\"#]+)");
    private static final Pattern MODULE_ID_PATTERN = Pattern.compile("^\\s*id\\s*:\\s*['\"]?([a-z0-9._-]+)['\"]?\\s*(?:#.*)?$");
    private static final String INTERNAL_PACK_METADATA = """
        {
          "pack": {
            "description": "Internal Donutgame module data",
            "min_format": [107, 1],
            "max_format": [107, 1]
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
        try {
            deleteDirectory(datapacksFolder);
        } catch (IOException exception) {
            context.getLogger().warn("Failed to clear generated module datapacks in " + datapacksFolder + ": " + exception.getMessage());
            return;
        }
        if (!Files.isDirectory(modulesFolder)) {
            return;
        }

        try (Stream<Path> files = Files.walk(modulesFolder)) {
            for (Path moduleJar : files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .sorted()
                .toList()) {
                discoverPack(context, registrar, modulesFolder, datapacksFolder, moduleJar);
            }
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

    private void discoverPack(BootstrapContext context, DatapackRegistrar registrar, Path modulesFolder, Path datapacksFolder, Path moduleJar) {
        try {
            String moduleId = readModuleId(moduleJar);
            Path directory = ModuleFiles.directory(modulesFolder, moduleId);
            ModuleFiles.seed(moduleJar, directory);
            String packId = "module-" + moduleId;
            Path packFolder = datapacksFolder.resolve(packId).normalize();
            if (!packFolder.startsWith(datapacksFolder)) {
                throw new IOException("Resolved datapack path escaped runtime folder: " + packFolder);
            }
            if (!extractDatapack(directory, packFolder, moduleId)) {
                return;
            }
            var discovered = registrar.discoverPack(packFolder, packId, configurer -> configurer
                .title(Component.text("Donutgame " + moduleId))
                .autoEnableOnServerStart(true)
                .position(false, Datapack.Position.TOP)
            );
            if (discovered != null) {
                try {
                    cacheBootstrapTitle(discovered, "Donutgame " + moduleId);
                } catch (ReflectiveOperationException exception) {
                    registrar.removeDiscoveredPack(discovered.getName());
                    throw new IOException("Could not prepare registry-independent datapack title", exception);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            context.getLogger().warn("Failed to discover module datapack " + moduleJar + ": " + exception.getMessage());
        }
    }

    private static void cacheBootstrapTitle(io.papermc.paper.datapack.DiscoveredDatapack discovered, String text)
        throws ReflectiveOperationException {
        // TODO: Paper 26.2 wraps titles lazily, but Pack.equals converts them before CraftRegistry exists.
        // TODO: Cache a native literal for this plain title; remove this workaround when upstream fixes bootstrap conversion.
        var packField = discovered.getClass().getDeclaredField("pack");
        packField.setAccessible(true);
        Object pack = packField.get(discovered);
        Object title = pack.getClass().getMethod("getTitle").invoke(pack);
        if (!title.getClass().getName().equals("io.papermc.paper.adventure.AdventureComponent")) return;
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Object literal = component.getMethod("literal", String.class).invoke(null, text);
        var vanilla = title.getClass().getDeclaredField("vanilla");
        vanilla.setAccessible(true);
        vanilla.set(title, literal);
    }

    private String readModuleId(Path moduleJar) throws IOException {
        try (JarFile jar = new JarFile(moduleJar.toFile())) {
            JarEntry configEntry = jar.getJarEntry("config.yml");
            if (configEntry == null || configEntry.isDirectory()) {
                throw new IOException("Module jar has no config.yml: " + moduleJar.getFileName());
            }
            String config;
            try (InputStream input = jar.getInputStream(configEntry)) {
                config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : config.lines().toList()) {
                Matcher matcher = MODULE_ID_PATTERN.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new IOException("Module config.yml has no valid id: " + moduleJar.getFileName());
        }
    }

    private boolean extractDatapack(Path directory, Path packFolder, String moduleId) throws IOException {
        Path data = ModuleFiles.resolve(directory, "data");
        if (!Files.isDirectory(data)) return false;
        List<Path> dataFiles;
        try (Stream<Path> files = Files.walk(data)) {
            dataFiles = files.filter(Files::isRegularFile).sorted().toList();
        }
        if (dataFiles.isEmpty()) return false;
        for (Path file : dataFiles) {
            String relative = directory.relativize(file).toString().replace('\\', '/');
            validateOwnedNamespace(relative, moduleId);
            ModuleFiles.resolve(directory, relative);
        }
        deleteDirectory(packFolder);
        Files.createDirectories(packFolder);
        Files.writeString(packFolder.resolve("pack.mcmeta"), INTERNAL_PACK_METADATA, StandardCharsets.UTF_8);
        for (Path file : dataFiles) {
            String relative = directory.relativize(file).toString().replace('\\', '/');
            Path target = ModuleFiles.resolve(packFolder, relative);
            Files.createDirectories(target.getParent());
            Files.copy(file, target);
        }
        return true;
    }

    private void validateOwnedNamespace(String entryName, String moduleId) throws IOException {
        String prefix = "data/" + moduleId + "/";
        if (!entryName.startsWith(prefix)) {
            throw new IOException(
                "Module '" + moduleId + "' may only contain datapack files under " + prefix + " (found " + entryName + ")."
            );
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
}
